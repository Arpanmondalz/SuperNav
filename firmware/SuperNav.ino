#include <Arduino.h>
#include <U8g2lib.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "icons.h"

// Initialize the OLED using your specific constructor and pins (SCL = D5, SDA = D4)
U8G2_SH1107_SEEED_128X128_1_HW_I2C u8g2(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);

// --- BLE UUIDs ---
// These are the unique IDs the Android app will look for. 
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// --- Global State Variables ---
String currentDistance = "Waiting...";
const unsigned char* currentIcon = icon_straight;
bool needsRedraw = true; // Only update the OLED when we get new data
bool deviceConnected = false;

// --- BLE Server Callback ---
class ServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      currentDistance = "Connected!";
      currentIcon = icon_destination;
      needsRedraw = true;
    };
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      currentDistance = "Lost Phone";
      currentIcon = icon_straight;
      needsRedraw = true;
      // Restart advertising so phone can reconnect
      BLEDevice::startAdvertising();
    }
};

// --- BLE Data Receiver Callback ---
class CharacteristicCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue().c_str();
      
      if (rxValue.length() > 0) {
        Serial.print("Received Data: ");
        Serial.println(rxValue);

        // We expect: CODE|DISTANCE|RAW_TEXT (e.g., "TR|70 m|Turn right")
        int firstPipe = rxValue.indexOf('|');
        int secondPipe = rxValue.indexOf('|', firstPipe + 1);

        if (firstPipe > 0) {
          // 1. Extract the Code
          String code = rxValue.substring(0, firstPipe);
          
          // 2. Extract the Distance (ignore everything after the second pipe)
          if (secondPipe > 0) {
            currentDistance = rxValue.substring(firstPipe + 1, secondPipe);
          } else {
            currentDistance = rxValue.substring(firstPipe + 1);
          }

          // 3. Update the icon based on the code
          updateIcon(code);
          
          // 4. Tell the main loop to redraw the screen
          needsRedraw = true;
        }
      }
    }

    // Helper function to map the string code to the C-array icon
    void updateIcon(String code) {
      if (code == "FO") currentIcon = icon_flyover;
      else if (code == "SVR") currentIcon = icon_service_road;
      else if (code == "RA") currentIcon = icon_roundabout_right; 
      else if (code == "UT") currentIcon = icon_u_turn_right; 
      else if (code == "SHR") currentIcon = icon_sharp_right;
      else if (code == "SHL") currentIcon = icon_sharp_left;
      else if (code == "SR" || code == "KR") currentIcon = icon_slight_right;
      else if (code == "SL" || code == "KL") currentIcon = icon_slight_left;
      else if (code == "TR") currentIcon = icon_turn_right;
      else if (code == "TL") currentIcon = icon_turn_left;
      else if (code == "MG") currentIcon = icon_merge;
      else if (code == "EX") currentIcon = icon_exit;
      else if (code == "ST") currentIcon = icon_straight;
      else if (code == "DEST") currentIcon = icon_destination;
      else currentIcon = icon_straight; // Default fallback
    }
};

void setup() {
  Serial.begin(115200);
  
  // 1. Initialize Display
  u8g2.begin();
  
  // 2. Initialize BLE
  BLEDevice::init("SuperNav"); // This is the name your phone will see!
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  BLECharacteristic *pCharacteristic = pService->createCharacteristic(
                                         CHARACTERISTIC_UUID,
                                         BLECharacteristic::PROPERTY_WRITE
                                       );

  pCharacteristic->setCallbacks(new CharacteristicCallbacks());
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("BLE Server Started. Waiting for connections...");
  
  // Draw initial "Waiting" screen
  drawDisplay();
}

void loop() {
  // Only redraw the screen when new data arrives to save CPU cycles
  if (needsRedraw) {
    drawDisplay();
    needsRedraw = false;
  }
  
  // Small delay to prevent the loop from hogging the watchdog timer
  delay(20); 
}

// --- Display Drawing Logic ---
void drawDisplay() {
  u8g2.firstPage();
  do {
    // 1. Draw the 64x64 Icon
    // Screen is 128px wide. (128 - 64) / 2 = 32 for perfect horizontal centering.
    // Placed at Y=10 to give it a little breathing room from the top edge.
    u8g2.drawBitmap(32, 10, 8, 64, currentIcon);

    // 2. Draw the Distance Text
    u8g2.setFont(u8g2_font_logisoso22_tr); // Big, readable font for numbers

    // Calculate text width to center it dynamically (so "70 m" and "1.2 km" both look good)
    int textWidth = u8g2.getStrWidth(currentDistance.c_str());
    int xPos = (128 - textWidth) / 2;
    
    // Placed at Y=115, nicely below the icon
    u8g2.drawStr(xPos, 115, currentDistance.c_str());
    
  } while ( u8g2.nextPage() );
}