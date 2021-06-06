# DoggyBot

This is a beginner level project that implements basic image processing methods to identify laser beam and communicate its coordinate and respective actions to track it from an android powered smart phone on-board to the Node MCU controller of the robot.

Android Project setup:
The code depends on OpenCV4Android SDK. Below link can be referred to setup OpenCV library with Android Studio,
https://medium.com/android-news/a-beginners-guide-to-setting-up-opencv-android-library-on-android-studio-19794e220f3c


Circuit connections to Node MCU:
![alt text](https://github.com/SriramEmarose/DoggyBot/blob/main/NodeMCU_Connections.png)


Flash below sketch to NodeMCU and update the App's MainActivity.java with respective IP address,
https://github.com/SriramEmarose/DoggyBot/blob/main/MotorController/MotorController.ino
