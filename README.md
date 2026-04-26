# Super Nav 
A custom DIY Heads-Up Display (HUD) for navigation. 

### Features
- **Real-time Navigation:** Pulls turn-by-turn data directly from Google Maps.
- **Low Latency:** High-speed BLE link between Android and Xiao ESP32C3.
- **High Visibility:** Custom 64x64 navigation icons on a 1.1\" SH1107 OLED.
- **Dark UI:** Modern, dark-mode Android companion app.

### Hardware
- Seeed Studio XIAO ESP32C3
- 1.12" OLED Display (SH1107)
- Custom 3D Printed Housing (Coming Soon)

### How to Build
1. **Firmware:** Flash the `.ino` sketch in `/firmware` to your Xiao ESP32C3.
2. **App:** Build and install the Android app
3. **Connect:** Open the app, grant notification permissions, and hit "Connect HUD".
