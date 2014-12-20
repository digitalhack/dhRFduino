#include <RFduinoBLE.h>

//#define DEBUG
#define DEBOUNCE 100
#define MAXREV 1600

// Setup status LEDs

#define REDLED 6
#define BLUELED 5
#define REDBLINKINT 100000
byte redLED = 0;
volatile byte blueLED = 0;
volatile byte saveBlue;
byte redBlink = 1;
int redBlinkCnt = REDBLINKINT;

volatile long lastRevMillis = 0;
volatile int millisPerRev = 0;
volatile int cnt = 0;
int oldMillisPerRev = 0;
int oldCnt = 0;
int rpm = 0;
boolean flagZeroSent = 0;

int pinISR(uint32_t ulPin) {
  if (lastRevMillis + DEBOUNCE < millis()) {
    if (lastRevMillis + MAXREV > millis()) {
      millisPerRev = millis() - lastRevMillis;
      cnt++;
    } 
    lastRevMillis = millis();
  }
}

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

void bleSendData (int cnt, int rpm) {
  union {
    int data[2];
    char buf[8];
  } bledata;

  bledata.data[0] = cnt;
  bledata.data[1] = rpm;
  RFduinoBLE.send(bledata.buf, 8); 
#ifdef DEBUG
  Serial.print(rpm);
  Serial.print(" ");
  Serial.println(cnt);
#endif
}

void setup(void) {
#ifdef DEBUG
  Serial.begin(9600);
  Serial.println("Starting...");
#endif

  pinMode(2, INPUT_PULLUP);
  RFduino_pinWakeCallback(2, LOW, pinISR);  

  // Set the status pins as output to power the LEDs
  pinMode(REDLED, OUTPUT);
  pinMode(BLUELED, OUTPUT);
  
  // Set the BLE advertisement and start the stack
  RFduinoBLE.advertisementData = "revs";
  RFduinoBLE.begin();

  oldCnt = cnt;
  oldMillisPerRev = millisPerRev;
  bleSendData(cnt, millisPerRev);
}

void loop(void) {
  // If we are blinking the red LED toggle it.  This will
  // cause the red LED to toggle each time we go through
  // the loop.  If the red LED turns stays off or on 
  // there is an error.
  if (redBlink) {
    if (redBlinkCnt-- < 0) {
      redLED = ! redLED;
      digitalWrite(REDLED, redLED);
      redBlinkCnt = REDBLINKINT;
    }
  }
 
  redBlink = 1;

  // If the raw data changes caculate the new temperature.
  // Update the value stored in the BLE stack and then rapid flash
  // the blue LED to denote that a new temperature is available.
  int tmp = millis() - lastRevMillis;
  if (tmp > MAXREV) {
    if (! flagZeroSent) {
      millisPerRev = -1;
      bleSendData(cnt, millisPerRev);
      flagZeroSent = 1;
    }
  } else if (cnt != oldCnt) {
    bleSendData(cnt, millisPerRev);
    oldCnt = cnt;
    oldMillisPerRev = millisPerRev;
    flagZeroSent = 0;
  }  

}
