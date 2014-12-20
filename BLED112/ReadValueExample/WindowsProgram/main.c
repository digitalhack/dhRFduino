// Need to handle read timeout better.

#include <stdio.h>
#include <windows.h>
#include "cmd_def.h"

//#define DEBUG
#define BLEADDRESS "d6:3e:7a:bd:b5:7d"
#define COMPORT "COM8"
//#define COMPORT "\\\\.\\COM10" // use this notation for com ports > 9.

typedef enum {
  state_disconnected,
  state_connecting,
  state_connected,
  state_requesting_value,
  state_value_returned,
  state_finish,
} states;
states state = state_disconnected;

bd_addr btle_dev;
uint8 btle_connection;
volatile HANDLE comm_port_h;

void dump_packet(char *rxtx, int len0, unsigned char *data0, int len1, unsigned char *data1) {
  printf("%s Packet: ", rxtx);
  int i;
  for (i = 0; i < len0; i++) {
    printf("%02x ", data0[i]);
  }

  for (i = 0; i < len1; i++) {
    printf("%02x ", data1[i]);
  }
  printf("\n");
}

void change_state(states new_state) {
  state = new_state;
}

void btle_send(uint8 len1, uint8* data1, uint16 len2, uint8* data2) {
  DWORD bytes_sent;

#ifdef DEBUG
  dump_packet("TX", len1, data1, len2, data2);
#endif

  if(!WriteFile (comm_port_h, data1, len1, &bytes_sent, NULL)) {
    printf("ERROR: Writing data. %d\n",(int)GetLastError());
    exit(-1);
  }

  if(!WriteFile (comm_port_h, data2, len2, &bytes_sent, NULL)) {
    printf("ERROR: Writing data. %d\n",(int)GetLastError());
    exit(-1);
  }
}

int btle_read() {
  DWORD bytes_read;
  const struct ble_msg *btle_msg;
  struct ble_header btle_msg_hdr;
  unsigned char buf[128];

#ifdef DEBUG
  printf("above first read\n");
#endif

  if(!ReadFile(comm_port_h, (unsigned char*)&btle_msg_hdr, 4, &bytes_read, NULL)) {
    return GetLastError();
  }
  // Error if 4 bytes were not read.  This means a timeout occurred.
  if (bytes_read != 4) return -1;

  if(btle_msg_hdr.lolen) {

#ifdef DEBUG
    printf("above second read\n");
#endif

    if(!ReadFile(comm_port_h, buf, btle_msg_hdr.lolen, &bytes_read, NULL)) {
      return GetLastError();
    }
  }

#ifdef DEBUG
  dump_packet("RX", sizeof(btle_msg_hdr), (unsigned char *)&btle_msg_hdr, btle_msg_hdr.lolen, buf);
#endif

  btle_msg=ble_get_msg_hdr(btle_msg_hdr);
  if(!btle_msg) {
    printf("ERROR: Message not found:%d:%d\n",(int)btle_msg_hdr.cls,(int)btle_msg_hdr.command);
    return -1;
  }

#ifdef DEBUG
  printf("dispatching event / response\n");
#endif

  btle_msg->handler(buf);

#ifdef DEBUG
  printf("below dispatch\n");
#endif

  return 0;
}

void ble_evt_connection_status(const struct ble_msg_connection_status_evt_t *msg) {
  if (msg->flags &connection_connected) {
    change_state(state_connected);

#ifdef DEBUG
    printf("Connected\n");
#endif
  }
}

void ble_evt_attclient_attribute_value(const struct ble_msg_attclient_attribute_value_evt_t *msg) {
  union {
    float f;
    int i;
    uint8 b[4];
  } value;

  value.b[0] = msg->value.data[0];
  value.b[1] = msg->value.data[1];
  value.b[2] = msg->value.data[2];
  value.b[3] = msg->value.data[3];

  printf("----> value: (int) %d (float) %f (byte) %c\n", value.i, value.f, value.b[0]);

  if (state == state_requesting_value) change_state(state_value_returned);
}

void ble_evt_connection_disconnected(const struct ble_msg_connection_disconnected_evt_t *msg) {
  change_state(state_disconnected);

#ifdef DEBUG
  printf("Connection terminated\n");
#endif
}

void ble_rsp_gap_connect_direct(const struct ble_msg_gap_connect_direct_rsp_t *msg) {

#ifdef DEBUG
  printf("from ble_rsp_gap_connect_direct - result: %d, connection_handle: %d\n",
         msg->result, msg->connection_handle);
#endif

  btle_connection = msg->connection_handle;
}

int main(int argc, char *argv[]) {
  COMMTIMEOUTS cto;
  char *comm_port = COMPORT;
  unsigned int b[6];
  int n;

 	n = sscanf(BLEADDRESS, "%X:%X:%X:%X:%X:%X", &b[5], &b[4], &b[3], &b[2], &b[1], &b[0]);
 	if (n == 6 && (b[0] | b[1] | b[2] | b[3] | b[4] | b[5]) < 256) {
    for(n=0; n<6; n++)
 	  	btle_dev.addr[n] = b[n];
 	} else {
 		printf("bad bluetooth address");
 		return -1;
 	}

  bglib_output = btle_send;

  comm_port_h = CreateFile(comm_port, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ|FILE_SHARE_WRITE,
                              NULL, OPEN_EXISTING, 0, NULL);


  if (comm_port_h == INVALID_HANDLE_VALUE) {
    printf("Error opening %s: %d\n",comm_port,(int)GetLastError());
    return -1;
  }

  // Reset dongle to get it into known state
  ble_cmd_system_reset(0);
  CloseHandle(comm_port_h);

  do {
    Sleep (500); // 0.5s
    comm_port_h = CreateFile(comm_port, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ|FILE_SHARE_WRITE,
                                NULL, OPEN_EXISTING, 0, NULL);
  } while (comm_port_h == INVALID_HANDLE_VALUE);

  // Set 10 second timeout for reads.
  cto.ReadIntervalTimeout = 0;
  cto.ReadTotalTimeoutMultiplier = 0;
  cto.ReadTotalTimeoutConstant = 10000;
  cto.WriteTotalTimeoutMultiplier = 0;
  cto.WriteTotalTimeoutConstant = 0;

  if (!SetCommTimeouts(comm_port_h, &cto)) {
    printf("Error setting port timeouts: %d\n",(int)GetLastError());
    return -1;
  }

#ifdef DEBUG
  printf("ReadIntervalTimeout: %ld, ReadTotalTimeoutConstant: %ld, ReadTotalTimeoutMultiplier %ld\n",
         cto.ReadIntervalTimeout, cto.ReadTotalTimeoutConstant, cto.ReadTotalTimeoutMultiplier);
#endif

  change_state(state_connecting);
  ble_cmd_gap_connect_direct(&btle_dev, gap_address_type_random, 40, 60, 100,0);
  while (state != state_connected) {
    if (btle_read() != 0) break;
  }

  change_state(state_requesting_value);
  ble_cmd_attclient_read_by_handle(btle_connection, 0x000e);
  while (state != state_value_returned) {
    if (btle_read() != 0) break;
  }

  ble_cmd_connection_disconnect(btle_connection);
  while (state != state_disconnected) {
    if (btle_read() != 0) break;
  }

  CloseHandle(comm_port_h);
  return 0;
}
