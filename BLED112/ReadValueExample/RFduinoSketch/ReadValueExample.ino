#define DEBUG 1
#include <RFduinoBLE.h>

// This function is defined in the RFduino BLE library so it gets
// called anytime a connect is completed to the RFduino.
void RFduinoBLE_onConnect(){
  Serial.println("Connect");
}

// This function is defined in the RFduino BLE library so it gets
// called any time the RFduino is disconnected from.
void RFduinoBLE_onDisconnect(){
  Serial.println("Disconnect"); 
}

void setup() {
  Serial.begin(9600);
  Serial.println("Starting...");
  // this is the data we want to appear in the advertisement
  RFduinoBLE.advertisementData = "test01";

  // start the BLE stack
  RFduinoBLE.begin();
}

// In loop we send either a byte, float of integer by uncommenting the appropriate line.
// As the code is setup now the RFduino will send an uppercase A when it is queried for data

void loop() {
  uint8_t temp = 'A';
  Serial.println("Setting value");
  RFduinoBLE.sendByte(temp);
  //RFduinoBLE.sendFloat(99.1);
  //RFduinoBLE.sendInt(32766);
  while (true) {
  }
}

