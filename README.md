# Jarvis Assistant — Phone-Only Setup (GitHub Actions, no computer needed)

This path builds the app entirely on GitHub's free cloud servers. Everything below is done from your Android phone.

## Part 1 — Create a free GitHub account

1. In your phone browser, go to https://github.com/signup and create an account (free).

## Part 2 — Install Termux (gives you a terminal on your phone)

Don't use the Play Store version (outdated). Instead:
1. Open your browser, go to https://f-droid.org/en/packages/com.termux/
2. Download and install Termux, and also https://f-droid.org/en/packages/com.termux.api/ (optional, not required for this).
3. You'll need to allow "install unknown apps" for your browser/file manager when prompted — this is normal for F-Droid apps.
4. Open Termux once installed.

## Part 3 — Get a GitHub Personal Access Token (acts as your password for pushing code)

1. In your phone browser: **GitHub.com > your profile photo > Settings > Developer settings > Personal access tokens > Tokens (classic) > Generate new token**.
2. Give it "repo" scope, generate it, and **copy the token somewhere safe** (you won't see it again). Treat it like a password.

## Part 4 — Create the empty repository on GitHub

1. Go to https://github.new
2. Name it `JarvisAssistant`, keep it **Public** (required for unlimited free Actions minutes) or Private (also free, just fewer minutes/month), don't add a README, click **Create repository**.

## Part 5 — Push the project from Termux

1. Get the `JarvisAssistant.zip` file (that I've generated) onto your phone's normal Downloads folder.
2. In Termux, run these commands one at a time:
   ```
   pkg update -y && pkg install -y git unzip
   termux-setup-storage
   ```
   (Approve the storage permission prompt.)
3. Unzip the project and move into it:
   ```
   cd ~/storage/downloads
   unzip JarvisAssistant.zip
   cd JarvisAssistant
   ```
4. Set your git identity (use your real GitHub email/username):
   ```
   git config --global user.email "you@example.com"
   git config --global user.name "yourusername"
   ```
5. Initialize and push:
   ```
   git init
   git add .
   git commit -m "Initial Jarvis project"
   git branch -M main
   git remote add origin https://github.com/YOURUSERNAME/JarvisAssistant.git
   git push -u origin main
   ```
   When it asks for a username, type your GitHub username. When it asks for a password, **paste the Personal Access Token** from Part 3 (not your real password).

## Part 6 — Trigger the build

1. Go to your repo on GitHub.com in the browser: `github.com/YOURUSERNAME/JarvisAssistant`
2. Tap the **Actions** tab. You should see "Build Jarvis APK" already queued or run automatically (it triggers on push).
3. If it's not running, tap it, then tap **Run workflow**.
4. Wait a few minutes — tap into the running job to watch the log live.

## Part 7 — Download the APK

1. Once the run finishes (green checkmark), open that run's page.
2. Scroll to **Artifacts** at the bottom → tap **jarvis-debug-apk** → it downloads a zip containing the APK to your phone.
3. Open your file manager, extract the zip, tap `app-debug.apk` to install (allow "install unknown apps" for your file manager if prompted).

That's it — no computer touched at any point.

## Troubleshooting the cloud build

- If the Actions build fails on an SDK/platform error, open the failed log — it usually names the missing SDK component. This is fixable by adding an `android-actions/setup-android` step to `.github/workflows/build.yml`, which I can add if you hit this.
- Free GitHub accounts get 2,000 Actions minutes/month on private repos, and unlimited on public repos — a build like this takes a few minutes, so you have plenty of room.

---

# (Reference) Desktop Setup — if you ever get computer access

1. Go to https://developer.android.com/studio and download Android Studio for your OS.
2. Run the installer, keep all default options (it will install the Android SDK, platform tools, and an emulator).
3. On first launch, go through the "Setup Wizard" — accept the SDK license, let it download components (~2-4 GB, takes a while).
4. Once open, go to **Settings > Languages & Frameworks > Android SDK** and confirm **Android 13 (API 33)** is checked/installed under SDK Platforms.

## Part 2 — Open this project

1. Unzip the project file I've given you.
2. In Android Studio: **File > Open** → select the unzipped `JarvisAssistant` folder.
3. Let Gradle sync (bottom status bar) — first sync can take several minutes as it downloads dependencies.
4. If Gradle complains about version mismatches, click "Update" / accept the suggested fix — it's normal for dependency versions to drift over time; adjust versions in `app/build.gradle.kts` if needed.

## Part 3 — Connect your Android 13 phone

1. On your phone: **Settings > About phone** → tap "Build number" 7 times to unlock Developer Options.
2. **Settings > System > Developer options** → enable **USB debugging**.
3. Plug the phone into your computer with a USB cable. Accept the "Allow USB debugging?" prompt on the phone.
4. In Android Studio, your phone should appear in the device dropdown (top toolbar). If not, try a different cable/port or install your phone brand's USB drivers.

## Part 4 — Wire up the AI brain (pick one or both)

### Option A: Claude API (better quality, needs internet)
1. Get an API key at https://console.anthropic.com (Settings > API Keys).
2. Build and install the app (see Part 5), open it, tap **Settings**, paste your key, save.
3. Note: for a personal-use app on your own phone this is fine. Don't publish this app publicly with the key baked in — anyone could extract it from the APK.

### Option B: Fully offline local model (no internet, weaker quality, slower)
1. Go to https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference and download a Gemma `.task` model (e.g. `gemma-2b-it-cpu-int4.task`). You'll need to accept Google's license (via Kaggle or Hugging Face).
2. With your phone connected via USB, run in a terminal:
   ```
   adb shell mkdir -p /data/local/tmp/llm
   adb push gemma-2b-it-cpu-int4.task /data/local/tmp/llm/gemma.task
   ```
3. In the app's Settings screen, toggle "Use offline local model instead."

## Part 5 — Wake word setup (optional)

1. Sign up free at https://console.picovoice.ai
2. Copy your **AccessKey** and paste it into `WakeWordService.kt` (`ACCESS_KEY` constant).
3. Either use Porcupine's built-in "Jarvis" keyword, or train a custom "Hey Jarvis" `.ppn` file in the Picovoice console and drop it into `app/src/main/assets/hey_jarvis.ppn`.
4. On the phone, after installing: **Settings > Apps > Jarvis > Battery** → set to "Unrestricted" so Android 13 doesn't kill the background listener.

## Part 6 — Build and install

1. In Android Studio, click the green **Run ▶** button with your phone selected as the target.
2. First launch: grant microphone and notification permissions when prompted.
3. That's it — tap the mic button to talk, or enable the wake-word switch.

## Part 7 — Getting an APK to sideload elsewhere

- **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
- Find the file at `app/build/outputs/apk/debug/app-debug.apk`.
- Transfer it to any Android 13 phone and install directly (enable "Install unknown apps" for whatever app you use to open it).

## About the HUD look

The main screen is a custom-drawn "reactor core" (`ReactorView.kt`) — rotating arcs, a
pulsing glow, and a waveform ring, all in Canvas, no image assets needed. It shifts
color/behavior by state:
- **Cyan, slow drift** — idle/standby
- **Green, reactive waveform** — listening to you
- **Violet, faster spin** — thinking (LLM call in flight)
- **Amber, reactive waveform** — speaking the reply
- **Red flash** — error

Tap the core to talk. A scrolling monospace terminal log underneath timestamps every
exchange. Everything is driven by hex values in `colors.xml` and one custom `View` —
easy to retheme (try icy blue/white, or a red "Ultron" palette) by just changing the
color tokens, no redesign needed.

## What's new: fully hands-free + more commands

- **Wake word is now ON by default** and runs fully in the background — say "Jarvis," it captures your command with no screen/tap needed, and speaks the reply. No activity popup, no manual trigger.
- **First-launch greeting** — the very first time you open the app, Jarvis introduces itself out loud.
- **New commands**: `"play <song>"` (opens a music app's voice search), `"text <number or contact> saying <message>"` (sends an SMS directly — needs SMS + Contacts permission, granted on first launch).
- **New icon**: a green circuit-hexagon on black, replacing the soft blue circle.
- **Crash hardening**: brain calls, actions, and TTS are now wrapped so a bad API response or missing TTS voice can't crash the whole app — it logs the error and recovers instead.

If your commit history looks like this since you deployed the previous version, it's the same repo — just push app/src changes and the new .github and drawable files, then re-run Actions.

## Choosing an AI brain (multiple providers supported)

Open Jarvis → Settings. Pick one backend and paste its key — you can store all four keys at once and just flip the radio button to switch anytime, no rebuild needed.

| Provider | Cost | Get a key |
|---|---|---|
| **Gemini** (Google) | Free tier is genuinely usable for personal assistant use | https://aistudio.google.com/apikey |
| **DeepSeek** | Very cheap; some accounts get promo credit | https://platform.deepseek.com |
| **Claude** (Anthropic) | Pay-as-you-go | https://console.anthropic.com |
| **OpenAI** | Pay-as-you-go, no free tier | https://platform.openai.com/api-keys |
| **Local (offline)** | Fully free, no key needed | See "fully free" section above |

If you want to start with zero cost, **Gemini is the best pick** of the paid-API options — sign up, generate a key, select "Gemini" in Settings, paste it, save.

## Known limitations / honest expectations

- The offline local model is a phone-sized model — it'll be noticeably less capable than Claude, and each reply can take a few seconds on mid-range hardware.
- Always-listening wake word will use more battery; Android 13 is aggressive about killing background services, so the battery-optimization exemption step matters.
- Device actions are intentionally minimal (open app, alarm, search) — extend `DeviceActions.kt` for more (e.g. Accessibility Service for reading/replying to notifications, which requires an extra user-granted permission with its own setup screen).
