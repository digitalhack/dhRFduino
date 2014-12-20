#include <OneWire.h>
#include <RFduinoBLE.h>

// Dallas One Wire...
// There are a whole bunch of people who have had their hands
// in developing the Dallas One Wire library.  See the RFduino
// forum entry for the one that works with the RFduino and
// the other two URLs for additional information.
// http://forum.rfduino.com/index.php?topic=103.msg261#msg261
// http://www.pjrc.com/teensy/td_libs_OneWire.html
// http://milesburton.com/Dallas_Temperature_Control_Library

//#define DEBUG

OneWire  ds(6);  // on pin 2 (a 4.7K resistor is necessary)
int16_t oldRaw;

// Setup status LEDs

#define REDLED 2
#define BLUELED 3
byte redLED = 0;
volatile byte blueLED = 0;
volatile byte saveBlue;
byte redBlink = 1;

// Turn on blue LED when a connection is made

void RFduinoBLE_onConnect(){
  blueLED = 1;
  saveBlue = 1;
  digitalWrite(BLUELED, blueLED);
#ifdef DEBUG
  Serial.println("Connect...");
#endif
}

// Turn off blue LED when the connection is terminated.

void RFduinoBLE_onDisconnect(){
  blueLED = 0;
  saveBlue = 0;
  digitalWrite(BLUELED, blueLED);
#ifdef DEBUG
  Serial.println("Disconnect...");
#endif
}


void setup(void) {
#ifdef DEBUG
  Serial.begin(9600);
  Serial.println("Starting...");
#endif
  
  // Set the status pins as output to power the LEDs
  pinMode(REDLED, OUTPUT);
  pinMode(BLUELED, OUTPUT);
  
  // Set the BLE advertisement and start the stack
  RFduinoBLE.advertisementData = "temp";
  RFduinoBLE.begin();

  oldRaw = -1;
}

void loop(void) {
  byte i;
  byte present = 0;
  byte data[12];
  int16_t raw = 0;
  float celsius;
  
  // If we are blinking the red LED toggle it.  This will
  // cause the red LED to toggle each time we go through
  // the loop.  If the red LED turns stays off or on 
  // there is an error.
  if (redBlink) {
    redLED = ! redLED;
    digitalWrite(REDLED, redLED);
  }
 
  // reset the one wire network, skip address search as we
  // only have one sensor on the network and then tell the
  // sensor to calculate the temperature.
  ds.reset();
  ds.skip();
  ds.write(0x44);
  
  // 12 bit resolution which is the default requires 750ms
  // to calculate the temperature
  delay(800);
  
  //reset the one wire network, skip address search and
  // tell the sensor to send us the scratch pad.
  ds.reset();
  ds.skip();
  ds.write(0xBE);

  for ( i = 0; i < 9; i++) {           // we need 9 bytes
    data[i] = ds.read();
  }

  // if a crc error occurs the red LED will stay on until
  // the next successful read.
  if (data[8] != OneWire::crc8(data,8)) {
    redBlink = 0;
    redLED = 1;
    digitalWrite(REDLED, redLED);
    return;
  }
  
  redBlink = 1;

  // Extract the raw data from the packet
  raw = (data[1] << 8) | data[0];
  byte cfg = (data[4] & 0x60);
  // at lower res, the low bits are undefined, so let's zero them
  // default is 12 bit resolution, 750 ms conversion time
  if (cfg != 0x60) {
    if (cfg == 0x00) raw = raw & ~7;  // 9 bit resolution, 93.75 ms
    else if (cfg == 0x20) raw = raw & ~3; // 10 bit res, 187.5 ms
    else if (cfg == 0x40) raw = raw & ~1; // 11 bit res, 375 ms
  }
  
  // If the raw data changes caculate the new temperature.
  // Update the value stored in the BLE stack and then rapid flash
  // the blue LED to denote that a new temperature is available.
  if (oldRaw != raw) {
    celsius = (float)raw / 16.0;
#ifdef DEBUG
    Serial.print("Temperature set to: "); Serial.print(celsius); Serial.println(" C");
#endif
    RFduinoBLE.sendFloat(celsius);
    oldRaw = raw;
    saveBlue = blueLED;
    for (i = 0; i < 10; i++) {
      blueLED = ! blueLED;
      digitalWrite(BLUELED, blueLED);
      delay(100);
    }
    blueLED = saveBlue;
    digitalWrite(BLUELED, blueLED);
 }
}
