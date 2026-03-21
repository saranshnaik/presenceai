# PresenceAI

> An intelligent mobile application leveraging machine learning and reinforcement learning to combat phone addiction through personalized, context-aware nudges.

[![Platform](https://img.shields.io/badge/platform-Android-brightgreen.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9+-purple.svg)](https://kotlinlang.org/)
[![API Level](https://img.shields.io/badge/API%20Level-24%2B-blue.svg)](https://www.android.com/)

## 🌐 Website
[Visit PresenceAI Website](https://your-website-link-here.com)

## Overview

PresenceAI is an Android application designed to help users reduce phone addiction by providing intelligent, personalized nudges. The app combines behavioral analytics, machine learning, and reinforcement learning to understand user patterns and deliver contextually appropriate interventions that encourage healthier phone usage habits.

## ✨ Key Features

- **Behavioral Analytics**: Tracks and analyzes user behavior patterns, application categories, and usage signals
- **Intelligent Nudges**: AI-powered suggestions using Google's Gemini API to provide personalized, context-aware interventions
- **Machine Learning Pipeline**: Online learning model that adapts to individual user patterns
- **Reinforcement Learning**: Multi-armed bandit algorithm for optimal nudge strategy selection
- **Real-time Monitoring**: Background service for continuous notification and usage tracking
- **Feedback Integration**: Learns from user responses to improve nudge effectiveness
- **Privacy-Focused**: App-level analytics and on-device learning

## 🏗️ Architecture

### Core Modules

```
presenceai/
├── analytics/              # Behavior tracking and signal extraction
│   ├── AppCategoryClassifier.kt
│   ├── BehaviorSignals.kt
│   ├── FeatureExtractor.kt
│   ├── NotificationTracker.kt
│   ├── SignalAggregator.kt
│   └── SignalRepository.kt
├── genai/                  # Generative AI integration
│   ├── GeminiNudge.kt
│   └── GenAiModule.kt
├── ml/                     # Machine learning pipeline
│   ├── FeatureEngineering.kt
│   ├── LrClassifier.kt
│   ├── ModelStore.kt
│   ├── OnlineLearner.kt
│   ├── PipelineRunner.kt
│   └── Schema.kt
├── rl/                     # Reinforcement learning (Bandit)
│   ├── Bandit.kt
│   ├── BanditAction.kt
│   ├── BanditConfig.kt
│   ├── BanditState.kt
│   ├── BanditStore.kt
│   ├── LabelResolver.kt
│   ├── NudgeFormat.kt
│   ├── RewardFunction.kt
│   ├── ScreenEvent.kt
│   └── PostNudgeObserver.kt
├── services/               # Android services and listeners
│   ├── MonitoringService.kt
│   ├── NotificationListener.kt
│   ├── NudgeFeedbackReceiver.kt
│   ├── FalseNegativeGuard.kt
│   └── FeedbackActivityMonitor.kt
└── ui/                     # User interface components
    └── viewmodel/          # ViewModel for MVVM architecture
```

### Data Flow

1. **Collection**: NotificationListener and MonitoringService collect usage data
2. **Analysis**: FeatureExtractor and SignalAggregator process raw signals
3. **Prediction**: LrClassifier predicts user addiction likelihood
4. **Decision**: Bandit algorithm selects optimal nudge strategy
5. **Generation**: GeminiNudge generates personalized messages
6. **Feedback**: User responses feed back into the learning loop

## 🛠️ Technology Stack

- **Language**: Kotlin
- **Framework**: Android Architecture Components (MVVM)
- **Machine Learning**: Custom online learning pipeline
- **Reinforcement Learning**: Multi-Armed Bandit Algorithm
- **Generative AI**: Google Gemini API
- **Build System**: Gradle with Kotlin DSL
- **Monitoring**: Background Services, Notification Listeners

## 📋 Prerequisites

- **Android SDK**: API Level 24 or higher
- **Java/Kotlin**: JDK 11 or higher
- **Gradle**: 8.0 or higher
- **Google Cloud Account**: For Gemini API access (optional)

## 🚀 Installation

### 1. Clone the Repository
```bash
git clone https://github.com/saranshnaik/presenceai.git
cd presenceai
```

### 2. Configure Gradle
```bash
chmod +x gradlew
```

### 3. Build the Project
```bash
./gradlew build
```

### 4. Run on Emulator or Device
```bash
./gradlew installDebug
```

### 5. Configure API Keys
Add your Gemini API key to the project configuration:
```properties
GEMINI_API_KEY=your_api_key_here
```

## 📱 Usage

### Starting the Monitoring Service
```kotlin
val intent = Intent(context, MonitoringService::class.java)
ContextCompat.startForegroundService(context, intent)
```

### Triggering a Nudge
The app automatically generates nudges based on detected usage patterns. Nudges are delivered through the notification system.

### Providing Feedback
Users can interact with nudges through the notification feedback receiver, which helps the model improve over time.

## 🔬 Algorithm Details

### Machine Learning Model
- **Type**: Logistic Regression with online learning
- **Features**: App category, time of day, notification frequency, usage duration, behavioral signals
- **Training**: Incremental updates on each user interaction

### Reinforcement Learning Strategy
- **Algorithm**: ε-Greedy Multi-Armed Bandit
- **Actions**: Different nudge formats and content strategies
- **Reward**: User engagement and behavior change metrics

### Nudge Generation
- **Engine**: Google's Gemini API
- **Inputs**: User context, behavior patterns, predicted addiction state
- **Output**: Personalized, contextually appropriate messages

## 📊 Data & Datasets

The project includes sample datasets:
- `presenceai_dataset.csv`: Core behavioral data
- `PRESENCE_AI_56k_FULL.csv`: Extended dataset with 56K records

## 🧪 Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 Project Structure Notes

- **Analytics**: Focuses on signal extraction and user behavior classification
- **GenAI**: Handles integration with generative AI models for nudge personalization
- **ML**: Contains the core machine learning pipeline and model management
- **RL**: Implements decision-making algorithms for nudge optimization
- **Services**: Manages background processes and system integration
- **UI**: Implements the user-facing interface and view models

## ⚖️ License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Team

PresenceAI is developed as part of the Code It Beyond Hackathon.

## 📧 Support

For questions or issues, please open an issue on the GitHub repository or contact the development team.

---

**Note**: This is an active research project. The algorithms and strategies are continuously being improved and evaluated for effectiveness.
