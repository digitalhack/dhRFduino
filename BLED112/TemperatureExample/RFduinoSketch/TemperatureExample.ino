#include <RFduinoBLE.h>

//#define DEBUG
float temp;
float oldtemp;

//#define DEBUG
  
void setup() {
  // this is the data we want to appear in the advertisement
  RFduinoBLE.advertisementData = "temp";

  // start the BLE stack
  RFduinoBLE.begin();
  Serial.begin(9600);
  
  // get first temperature and pass to BLE
  temp = RFduino_temperature(FAHRENHEIT);
  oldtemp = temp;
  RFduinoBLE.sendFloat(temp);
}

void loop() {
  // sample once per second
  RFduino_ULPDelay( SECONDS(1) );
  temp = RFduino_temperature(FAHRENHEIT);
  
  // only update temp with BLE if it changes.  This
  // will allow BLE notify to only send the temperature
  // when it changes.
  if (temp != oldtemp) {
#ifdef DEBUG
    Serial.print("Temperature change, old temperature: ");
    Serial.print(oldtemp);
    Serial.print(" new temperature: ");
    Serial.println(temp);
#endif
    oldtemp = temp;
    RFduinoBLE.sendFloat(temp);
  }  
}
