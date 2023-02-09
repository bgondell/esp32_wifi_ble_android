# ESP32 WiFi credential setup over BLE
Setup your ESP32 WiFi credentials over BLE from an Android phone or tablet.
Sometimes you do not want to have your WiFi credentials in the source code, specially if it is open source and maybe accessible as a repository on Github or Bitbucket.

There are already solution like [WiFiManager-ESP32](https://github.com/zhouhan0126/WIFIMANAGER-ESP32) that give you the possibility to setup the WiFi credentials over a captive portal.    
But I wanted to test the possibility to setup the ESP32's WiFi over Bluetooth Low Energy.    
This repository covers the source code for the Android device. The source code for the ESP32 application are in the [ESP32_WiFi_BLE_ESP32](https://bitbucket.org/beegee1962/esp32_wifi_ble_esp32) repository.    

Detailed information about this project are on my [website](https://desire.giesecke.tk/index.php/2018/04/06/esp32-wifi-setup-over-ble/) 

## Development platform
PlatformIO, but as the whole code is in a single file it can be easily copied into a .ino file and used with the Arduino IDE

## Used hardware
- [Elecrow ESP32 WIFI BLE BOARD WROOM](https://circuit.rocks/esp32-wifi-ble-board-wroom.html?search=ESP32)		
- Any Android phone or tablet that is capable of BLE.		

## SW practices used
- Use of BLE for sending and receiving data

## Library dependencies		
Library name / Github link    
- [RxAndroidBle by Polidea](https://github.com/Polidea/RxAndroidBle)		