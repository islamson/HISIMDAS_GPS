# GPS Tracking and Data Logging Application

This project is an Android application developed in **Kotlin** using **Jetpack Compose**, designed to:

- Collect high-frequency GPS sensor data,
- Apply simple filtering techniques to ensure data stability,
- Calculate **instantaneous speed** (km/h) and **total distance traveled** (meters),
- **Visualize** the speed over distance on a real-time graph,
- **Log** valid data points (based on a speed difference filter) into a local **CSV file**,
- **Send** the generated CSV file automatically via email after tracking is stopped.

---

## ✨ Key Features

- **High-Frequency GPS Sampling:**  
  GPS location updates are requested every 100–500 milliseconds for fine-grained data collection.

- **Speed & Distance Calculation:**  
  Instantaneous speed is calculated from consecutive GPS points without relying on device-provided speed, ensuring better control over accuracy.

- **Noise Filtering:**  
  A **Speed Difference Filter** is applied:
  - If the speed change between two updates exceeds a threshold (e.g., 40 km/h), the new point is rejected to avoid recording GPS noise.

- **CSV Data Logging:**  
  For each accepted data point:
  - Timestamp (`YYYY-MM-DDTHH:mm:ss` format),
  - Latitude,
  - Longitude,
  - Distance traveled (meters),
  - Instantaneous speed (km/h),
  are logged into a local CSV file.

- **Email Export:**  
  When tracking stops, the CSV file is attached and automatically sent to a predefined email address (e.g., `islamoglu@hisim.com.tr`) via SMTP.

- **Beautiful Graph Visualization:**  
  Speed is plotted against distance using `co.yml.charts` library, providing real-time feedback to the user.

---

## 🚀 Technologies Used

- Kotlin
- Jetpack Compose
- Google FusedLocationProvider API (for GPS)
- JavaMail API (SMTP integration)
- YCharts (`co.yml.charts`) for graphing
- Git for version control

---

## 📦 How it Works

1. **Start Tracking:**
   - When the user presses the **Start** button, GPS updates begin.
   - A new CSV file is created in the app's external `Documents/` directory.

2. **During Tracking:**
   - Location updates are collected.
   - Speed and distance are calculated.
   - Only valid points (passing the speed difference filter) are added to the graph and logged into the CSV.

3. **Stop Tracking:**
   - Tracking stops.
   - The CSV file is closed.
   - The CSV file is automatically attached and sent via email.

---

## 📂 CSV File Structure

Each row in the CSV file contains:

| Timestamp | Latitude | Longitude | Position (m) | Speed (km/h) |
|:---------|:---------|:----------|:------------|:------------|
| 2024-08-07T13:40:40 | 40.123456 | 29.987654 | 3240 | 58 |

