# PresenceAI

PresenceAI is an Android application built during the **HackCrux 2026** Hackathon organized by **LNMIIT, Jaipur** to address phone overuse and phubbing. It uses behavioral signals, machine learning, reinforcement learning, and generative AI to decide when a user may benefit from a nudge and what kind of nudge to show.

The goal was to move beyond fixed screen-time reminders and build a system that adapts to individual usage patterns.

## Features

- Tracks app usage and notification-related behavioral signals
- Classifies applications and extracts behavioral features
- Uses an online logistic regression model to estimate the user's current state
- Uses an ε-greedy multi-armed bandit to select nudge strategies
- Generates personalized nudges using Google's Gemini API
- Collects feedback from user interactions with nudges
- Continuously updates the learning components based on new interactions
- Runs monitoring and notification handling through Android background services

## Architecture

The project is divided into a few main modules:

```text
presenceai/
├── analytics/
│   ├── AppCategoryClassifier.kt
│   ├── BehaviorSignals.kt
│   ├── FeatureExtractor.kt
│   ├── NotificationTracker.kt
│   ├── SignalAggregator.kt
│   └── SignalRepository.kt
├── genai/
│   ├── GeminiNudge.kt
│   └── GenAiModule.kt
├── ml/
│   ├── FeatureEngineering.kt
│   ├── LrClassifier.kt
│   ├── ModelStore.kt
│   ├── OnlineLearner.kt
│   ├── PipelineRunner.kt
│   └── Schema.kt
├── rl/
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
├── services/
│   ├── MonitoringService.kt
│   ├── NotificationListener.kt
│   ├── NudgeFeedbackReceiver.kt
│   ├── FalseNegativeGuard.kt
│   └── FeedbackActivityMonitor.kt
└── ui/
    └── viewmodel/
```

## How It Works

The main pipeline is:

```text
Usage / Notification Signals
            │
            ▼
     Feature Extraction
            │
            ▼
    Logistic Regression
            │
            ▼
    User State / Risk Estimate
            │
            ▼
   Multi-Armed Bandit Decision
            │
            ▼
       Nudge Selection
            │
            ▼
      Gemini Nudge Generation
            │
            ▼
       User Interaction
            │
            ▼
          Feedback
            │
            └──────────► Learning Loop
```

### 1. Behavioral Signals

The analytics module collects signals such as application usage, notification activity, timing, and other usage patterns. These signals are processed into features for the ML pipeline.

### 2. Machine Learning

The `ml` module contains an online logistic regression pipeline. Rather than relying entirely on a fixed model, the learner can update its parameters as new interaction data becomes available.

### 3. Reinforcement Learning

The `rl` module uses an **ε-greedy multi-armed bandit** to choose between available nudge strategies.

The available actions represent different nudge formats or strategies. A reward function evaluates the resulting interaction and feeds that information back into the bandit.

### 4. Generative AI

The selected nudge is generated through the **Google Gemini API** using the available user context and behavioral information.

### 5. Feedback

The application observes what happens after a nudge and uses the resulting feedback as part of the learning process.

## Technology Stack

- **Kotlin**
- **Android**
- **Android Architecture Components / MVVM**
- **Gradle**
- **Logistic Regression**
- **Online Learning**
- **ε-Greedy Multi-Armed Bandit**
- **Google Gemini API**
- **Android Background Services**
- **Notification Listener**

## Requirements

- Android SDK with API Level 24 or higher
- JDK 11 or higher
- Gradle 8.0 or higher
- A Gemini API key for the generative-AI functionality

Some features depend on Android permissions for usage monitoring and notification access.

## Setup

Clone the repository:

```bash
git clone https://github.com/saranshnaik/presenceai.git
cd presenceai
```

Make the Gradle wrapper executable on Linux/macOS:

```bash
chmod +x gradlew
```

Build the project:

```bash
./gradlew build
```

Install the debug build on a connected device or emulator:

```bash
./gradlew installDebug
```

Configure the Gemini API key using the project's local configuration before running the application. Do not commit the key to the repository.

## Usage

After installing the application, grant the permissions required for usage monitoring and notification access.

The application then monitors the relevant signals in the background. When the system determines that a nudge is appropriate, the bandit selects a nudge strategy and the Gemini integration generates the corresponding message.

Users can interact with the nudge, and the resulting feedback is used by the learning components.

## Machine Learning

The ML component uses:

- **Model:** Logistic Regression
- **Learning:** Online/incremental updates
- **Features:** Application category, time-related signals, notification activity, usage duration, and other behavioral signals

The model is intended to adapt to individual usage patterns rather than treating every user identically.

## Reinforcement Learning

The reinforcement-learning component uses an **ε-greedy multi-armed bandit**.

At a high level:

```text
Available Nudge Strategies
          │
          ▼
   Bandit selects action
          │
          ▼
    Nudge is delivered
          │
          ▼
     User response
          │
          ▼
      Reward value
          │
          ▼
   Update bandit state
```

This allows the application to gradually favor nudge strategies that produce better outcomes for a particular user.

## Generative AI

The Gemini integration is handled by the `genai` module.

The model receives relevant context from the application's processing pipeline and produces the final nudge text. The intention is to make the intervention more specific to the user's situation instead of relying on a fixed collection of messages.

## Data

The repository contains sample datasets used during development, including:

```text
presenceai_dataset.csv
PRESENCE_AI_56k_FULL.csv
```

These datasets contain behavioral data used for developing and evaluating the project's ML components.

## Testing

Run the unit tests with:

```bash
./gradlew test
```

For instrumented Android tests:

```bash
./gradlew connectedAndroidTest
```

## Hackathon

PresenceAI was developed as a project for the **HackCrux 2026** Hackathon.

The project focused on combining on-device behavioral analysis with adaptive ML/RL techniques and generative AI to create a more context-aware approach to reducing problematic phone usage.

## Team

**Protoc01**

## Notes

PresenceAI is a hackathon project and should be treated as a prototype rather than a production digital-wellbeing or medical system.

The repository contains experimental components and implementation decisions made under hackathon constraints. The ML and reinforcement-learning components are intended to demonstrate the adaptive approach rather than provide a clinically validated measure of phone addiction.
