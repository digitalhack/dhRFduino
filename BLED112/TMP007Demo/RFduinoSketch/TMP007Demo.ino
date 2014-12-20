#define DEBUG 1
#include <RFduinoBLE.h>
#include <Wire.h>
#include "Adafruit_TMP007.h"

Adafruit_TMP007 tmp007;
bool gotDevice = false;
float tempReading;
float oldTempReading;

#ifdef DEBUG
void RFduinoBLE_onConnect(){
  Serial.println("Connect");
}

void RFduinoBLE_onDisconnect(){
  Serial.println("Disconnect"); 
}
#endif

void setup() {
#ifdef DEBUG
  Serial.begin(9600);
#endif
  // this is the data we want to appear in the advertisement
  RFduinoBLE.advertisementData = "temp";

  // start the BLE stack
  RFduinoBLE.begin();
  
  if (tmp007.begin()) {
    gotDevice = true;
    delay(4000);
    tempReading = tmp007.readObjTempC();
    RFduinoBLE.sendFloat(tempReading);
  } else {
    tempReading = -999;
  }
  oldTempReading = tempReading;

#ifdef DEBUG
    Serial.print(tempReading); Serial.println(" C");
#endif
}

void loop() {
  while (true) {
    // sample once per 4 seconds
    delay(4000);
  
    if (gotDevice) {
      tempReading = tmp007.readObjTempC();
    } else {
      tempReading = -999.0;
    }

    if (tempReading != oldTempReading) {
      RFduinoBLE.sendFloat(tempReading);
      oldTempReading = tempReading;
#ifdef DEBUG
      Serial.print(tempReading); Serial.println(" C Temperature Changed");
    } else {
      Serial.print(tempReading); Serial.println(" C");
#endif  
    }
  }
}

