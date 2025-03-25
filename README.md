# Yolov11-Android-Tool-Kotlin
## YOLO Object Detection and Segmentation using TensorFlow Lite in Kotlin
This is an Android Studio project utilizing Kotlin and TensorFlow Lite (TFLite) to implement YOLO (You Only Look Once) for object detection and segmentation. The project aims to provide a lightweight and efficient solution for on-device computer vision tasks.

### Features
- Real-Time Object Detection: Perform fast and accurate object detection using a TFLite YOLO model.
- Segmentation: Includes segmentation capabilities to differentiate object boundaries.
- Zoom Slidebar: Interactive zoom functionality allows users to dynamically zoom in and out for a closer look at detected objects.
- Image Capture: Users can capture images directly from the camera, with the detected objects and segmentation overlays applied.
- Kotlin Implementation: Fully implemented in Kotlin for seamless Android integration.
- User-Friendly Interface: Minimal setup for easy use and extension.
- On-Device Processing: No network dependency—ideal for privacy-focused applications.
### Prerequisites
To run this project, you'll need:
- Android Studio (Latest version recommended).
- Android device running API level 21 (Lollipop) or higher.
- TensorFlow Lite YOLO model (.tflite file).

### Setup and Installation
1. Clone this repository:
```
git clone https://github.com/YourUsername/YourRepositoryName.git
cd YourRepositoryName
```
2. Open the project in Android Studio.
3. Add your YOLO model ( file) to the  folder.
4. Update the model's configuration in the code as needed.
5. Build and run the project on your Android device.

### How it Works
1. The app uses TensorFlow Lite's Interpreter to load and run the YOLO model
2. The camera feed is captured and processed in real time.
3. Detected objects and segmented regions are displayed on the screen.
4. Users can adjust the zoom level using the slidebar for a closer or wider view of the scene.
5. Captured images are saved with overlays for further reference.

### Build APK
Follow these steps to create the APK for this app:
1. Open the project in Android Studio.
2. Go to Build > Build Bundle(s)/APK(s) > Build APK(s).
3. Wait for the build process to complete.
4. Navigate to the generated APK file in the app/build/outputs/apk directory.
5. Transfer the APK to your Android device to install and test the app.

## Screenshots
<div align="center">
  <img src="https://raw.githubusercontent.com/AISoltani/Yolov11-Android-Tool-Kotlin/refs/heads/main/SCR1.jpg" alt="Image 1" width="400"/>
  <img src="https://raw.githubusercontent.com/AISoltani/Yolov11-Android-Tool-Kotlin/refs/heads/main/SCR2.jpg" alt="Image 2" width="400"/>
</div>

## License
This project is licensed under the MIT License - see the LICENSE file for details
