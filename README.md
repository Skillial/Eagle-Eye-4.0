# Eagle Eye 4.0: Mobile Sandbox for Image Enhancement

**Jean Louis Lance Cabrera, Kenshin Fetalvero, Brian Gabini, Jeremy Cerwin Wang**  
**Adviser:** Dr. Neil Patrick Del Gallego  
**De La Salle University**



## 📄 Abstract

Modern smartphones now offer computational power comparable to desktop systems, enabling a wide range of mobile applications and making in-device photography increasingly widespread. With over six billion active users worldwide, capturing and sharing high-quality images has become central to digital life. However, mobile cameras still face challenges such as limited resolution, haze, shadows, and noise, which can compromise image clarity and visibility. To address these limitations, we developed Eagle Eye 4.0, a mobile camera application that integrates enhancement techniques including super-resolution, dehazing, shadow removal, and denoising, while utilizing design principles from existing camera applications. We evaluated the performance of the ported algorithms by focusing on image quality and verifying that the application worked reliably across a range of devices, including low-end, mid-range, and high-end smartphones. The results demonstrate that the algorithms were successfully adapted for mobile devices with minimal loss in performance. User feedback further demonstrates that the application is well received and suitable for use across a broad range of smartphones.


## 🚀 Implementation Details

- **Language:** Kotlin 1.9.22  
- **Camera API:** Android Camera2 (`android.hardware.camera2`)  
- **Image Processing:** OpenCV 4.10.0  
- **Deep Learning Inference:** ONNX Runtime 1.20.0  

## 🔧 Getting Started

To run the project locally on your Android device:

1. **Clone the Repository**  
   ```bash
   git clone https://github.com/DLSU-GAME-Lab/EagleEye-4.0.git
   ```

2. **Open in Android Studio**  
   - Open Android Studio.  
   - Click **"Open an existing project"** and select the cloned folder.

3. **Sync and Build**  
   - Let Gradle finish syncing.  
   - Connect your Android device (with developer mode enabled), or use an emulator.

4. **Run the App**  
   - Press **Run ▶️** or use **Shift + F10**.

## 🧪 Tested On

- Honor Magic 5 Pro (high-end)
- Redmi Note 11 Pro 5G (mid-range)
- Samsung Galaxy A05 (entry-level)

## 📂 Project Structure

- [`app/src/main/java/com/wanggang/eagleeye`](app/src/main/java/com/wanggang/eagleeye) — Main Kotlin source code  
- [`app/src/main/assets/model/`](app/src/main/assets/model) — ONNX model files  
- [`app/src/main/res/`](app/src/main/res) — Layouts, drawables, and other UI resources  
- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — Permissions and app configuration

## 🌐 More Information
View this page for more info: https://skillial.github.io/Eagle-Eye-4.0

## 🙏 Acknowledgements

We would like to acknowledge **De La Salle University (DLSU)**, the **Department of Science and Technology (DOST)**, and the **Google Cloud Research Program** for funding and supporting this research.
