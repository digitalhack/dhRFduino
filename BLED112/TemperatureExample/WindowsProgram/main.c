#include <stdio.h>
#include <windows.h>
#include <signal.h>

#include "cmd_def.h"

//#define DEBUG
#define BLEADDRESS "d6:3e:7a:bd:b5:7d"
//#define COMPORT "COM8"
#define COMPORT "\\\\.\\COM12" // use this notation for com ports > 9.

#define BLTE_ERROR_READ_TIMEOUT 0x20000001

#define BLTE_STANDARD_TIMEOUT 5000

typedef enum {
  state_disconnected,
  state_connecting,
  state_connected,
  state_requesting_value,
  state_value_returned,
  state_write_pending,
  state_write_failed,
  state_write_success,
  state_finish,
} blte_states;
blte_states blte_state = state_disconnected;

bd_addr btle_dev;
uint8 btle_connection;
uint16 blte_error;
volatile HANDLE comm_port_h;
int partial_read;
long last_packet_read;

union {
  float f;
  int long_int;
  union {
    short short_int;
    short skip;
  };
  uint8 b[4];
} blte_value;

static int loop = 1;

void intHandler(int signal) {
  loop = 0;
}

long millis(void) {
  #include <sys\timeb.h>
  static struct timeb t_start, t_current;
  static int flag = 0;

  if (!flag) {
    ftime(&t_start);
    flag = 1;
    return 0;
  } else {
    ftime(&t_current);
    return (long) (1000.0 * (t_current.time - t_start.time)
        + (t_current.millitm - t_start.millitm));
  }
}

int bytes_available(HANDLE handle) {
  DWORD temp;
  COMSTAT comstat;

  if(!ClearCommError(handle, &temp, &comstat)) {
    printf("ERROR: bytes_available failed.  ClearCommError - GetLastError: %ld\n", GetLastError());
    return 0;
  }
  if (temp > 0) {
    printf("ERROR: bytes_available failed. ClearCommError - lpErrors: %ld\n", temp);
    return 0;
  }
  return comstat.cbInQue;
}

void blte_change_state(blte_states new_state) {
  blte_state = new_state;
}

#ifdef DEBUG
void blte_dump_packet(char *rxtx, int len0, unsigned char *data0, int len1, unsigned char *data1) {
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
#endif

void btle_send(uint8 len1, uint8* data1, uint16 len2, uint8* data2) {
  DWORD bytes_sent;

#ifdef DEBUG
  blte_dump_packet("TX", len1, data1, len2, data2);
#endif

  if(!WriteFile (comm_port_h, data1, len1, &bytes_sent, NULL)) {
    printf("FATAL ERROR: Writing data. GetLastError: %ld\n", GetLastError());
    exit(-1);
  }

  if(!WriteFile (comm_port_h, data2, len2, &bytes_sent, NULL)) {
    printf("FATAL ERROR: Writing data. GetLastError: %ld\n", GetLastError());
    exit(-1);
  }
}

int btle_read(int timeout) {
  DWORD bytes_read;
  const struct ble_msg *btle_msg;
  static struct ble_header btle_msg_hdr;
  unsigned char buf[128];

  if (millis() - last_packet_read > timeout) {
    return BLTE_ERROR_READ_TIMEOUT;
  }

  if (!partial_read) {
    if (bytes_available(comm_port_h) > 3) {
#ifdef DEBUG
      printf("header read read\n");
#endif
      if(!ReadFile(comm_port_h, (unsigned char*)&btle_msg_hdr, 4, &bytes_read, NULL)) {
        return GetLastError();
      }
      partial_read = 1;
    }
  }

  if (partial_read) {
    if(btle_msg_hdr.lolen) {
      if (bytes_available(comm_port_h) >= btle_msg_hdr.lolen) {

#ifdef DEBUG
        printf("data read\n");
#endif

        if(!ReadFile(comm_port_h, buf, btle_msg_hdr.lolen, &bytes_read, NULL)) {
          return GetLastError();
        }
        partial_read = 0;
        last_packet_read = millis();
      }
    } else {
      partial_read = 0;
      last_packet_read = millis();
    }

#ifdef DEBUG
  blte_dump_packet("RX", sizeof(btle_msg_hdr), (unsigned char *)&btle_msg_hdr, btle_msg_hdr.lolen, buf);
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
  }
  return 0;
}

int blte_read_by_handle(uint8 btle_connection, uint16 blte_handle) {
  blte_change_state(state_requesting_value);
  ble_cmd_attclient_read_by_handle(btle_connection, blte_handle);
  while (blte_state != state_value_returned) {
    if (btle_read(BLTE_STANDARD_TIMEOUT) != 0) break;
  }
  return 0;
}

void blte_notify_event() {
  printf("Temperature: %5.2f\n", blte_value.f);
}

// ble callback functions...

void ble_evt_attclient_attribute_value(const struct ble_msg_attclient_attribute_value_evt_t *msg) {
  blte_value.b[0] = msg->value.data[0];
  blte_value.b[1] = msg->value.data[1];
  blte_value.b[2] = msg->value.data[2];
  blte_value.b[3] = msg->value.data[3];

  if (msg->type == attclient_attribute_value_type_notify) {
    blte_notify_event();
  } else {
    if (blte_state == state_requesting_value) blte_change_state(state_value_returned);
  }
}

void ble_evt_connection_disconnected(const struct ble_msg_connection_disconnected_evt_t *msg) {
  blte_change_state(state_disconnected);

#ifdef DEBUG
  printf("Connection terminated\n");
#endif
}

void ble_evt_connection_status(const struct ble_msg_connection_status_evt_t *msg) {
  if (msg->flags &connection_connected) {
    blte_change_state(state_connected);

#ifdef DEBUG
    printf("Connected\n");
#endif
  }
}

void ble_rsp_attclient_write_command(const struct ble_msg_attclient_write_command_rsp_t *msg) {
#ifdef DEBUG
  printf("from ble_rsp_attclient_write_command - result: %d, connection_handle: %d\n",
         msg->result, msg->connection);
#endif
  if (msg->result == 0) {
    blte_state = state_write_success;
    blte_error = 0;
  } else {
    blte_state = state_write_failed;
    blte_error = msg->result;
  }
}

void ble_rsp_gap_connect_direct(const struct ble_msg_gap_connect_direct_rsp_t *msg) {
#ifdef DEBUG
  printf("from ble_rsp_gap_connect_direct - result: %d, connection_handle: %d\n",
         msg->result, msg->connection_handle);
#endif
  btle_connection = msg->connection_handle;
}

// end ble callback functions...

int main(int argc, char *argv[]) {
  char *comm_port = COMPORT;
  unsigned int b[6];
  int n;
  long error;

  partial_read = 0;

  //Call millis once at the start of main to initialize the start time.
  millis();

  // Trap ctrl-c to allow for clean up before existing.
  signal(SIGINT, intHandler);

 	// Convert the bluetooth address
 	n = sscanf(BLEADDRESS, "%X:%X:%X:%X:%X:%X", &b[5], &b[4], &b[3], &b[2], &b[1], &b[0]);
 	if (n == 6 && (b[0] | b[1] | b[2] | b[3] | b[4] | b[5]) < 256) {
    for(n=0; n<6; n++)
 	  	btle_dev.addr[n] = b[n];
 	} else {
 		printf("FATAL ERROR: Bad bluetooth address\n");
 		return -1;
 	}

  // Setup an output routine for the API
  bglib_output = btle_send;

  comm_port_h = CreateFile(comm_port, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ|FILE_SHARE_WRITE,
                              NULL, OPEN_EXISTING, 0, NULL);


  if (comm_port_h == INVALID_HANDLE_VALUE) {
    printf("FATAL ERROR: Unable to open %s: %d\n",comm_port,(int)GetLastError());
    return -1;
  }

  last_packet_read = millis();

  // Soft reset
  ble_cmd_connection_disconnect(0);
  ble_cmd_gap_set_mode(0, 0);
  ble_cmd_gap_end_procedure();

  blte_change_state(state_connecting);
  ble_cmd_gap_connect_direct(&btle_dev, gap_address_type_random, 40, 60, 100,0);
  while (blte_state != state_connected) {
    if((error = btle_read(BLTE_STANDARD_TIMEOUT)) != 0) break;
  }

  if (error == BLTE_ERROR_READ_TIMEOUT) {
    printf("FATAL ERROR: Timeout on connect\n");
    return error;
  }

#ifdef DEBUG
  // Get notify value
  blte_read_by_handle(btle_connection, 0x000f);
  printf("notify before write: %d\n", blte_value.short_int);
#endif

  // Set notify value
  blte_state = state_write_pending;
  blte_value.short_int = 1;
  ble_cmd_attclient_write_command(btle_connection, 0x000f, 2, &blte_value.short_int);
  while (blte_state == state_write_pending) {
    if((error = btle_read(BLTE_STANDARD_TIMEOUT)) != 0) break;
  }

  if (error == BLTE_ERROR_READ_TIMEOUT) {
    printf("FATAL ERROR: Timeout on set notify\n");
  // Ensure notify is set
  } else if (blte_state == state_write_failed) {
    printf("FATAL ERROR: Unable to set notify.  API error code: %d\n", blte_error);
    error = -1;
  } else {
    blte_read_by_handle(btle_connection, 0x000f);
#ifdef DEBUG
    printf("notify after write: %d\n", blte_value.short_int);
#endif
    if (blte_value.short_int != 1) {
      printf("FATAL ERROR: Unable to set notify\n");
      error = -1;
    } else {
      // Get Temperature value at start
      blte_read_by_handle(btle_connection, 0x000e);
      blte_notify_event();

      // Process norifies as they come in
      while (loop) {
        error = btle_read(BLTE_STANDARD_TIMEOUT);
        if ((error != 0) && (error != BLTE_ERROR_READ_TIMEOUT)) break;
      }
    }
  }

  error = 0;
  ble_cmd_connection_disconnect(btle_connection);
  while (blte_state != state_disconnected) {
    if((error = btle_read(BLTE_STANDARD_TIMEOUT)) != 0) break;
  }

  if (error == BLTE_ERROR_READ_TIMEOUT) {
    printf("FATAL ERROR: Timeout on disconnect\n");
  }

  CloseHandle(comm_port_h);
  return error;
}
