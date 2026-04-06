# Event Lottery System

Event Lottery System is a role-based Android event platform built with Java, Firebase, and AndroidX.

The app supports three personas:
- Entrants (discover events, join waitlists, manage invites)
- Organizers (create/manage events, run lotteries, communicate with entrants)
- Admins (moderate users/events and audit notifications)

## Major Features

### 1) Role-based onboarding and login
- Device-based identity using Android ID.
- Login routing for `ENTRANT`, `ORGANIZER`, and `ADMIN`.
- Signup flow with validation (details + address).
- Role-granting for existing users without duplicating user records.
- Admin-login flow that validates an admin key from Firestore (`app_config/admin_key`).

### 2) Entrant experience
- Event browse screen with status-aware cards (`open`, `full`, `closed`, etc.).
- Advanced filtering:
  - Capacity buckets
  - Time-of-day availability
  - Eligibility (`all` vs `joinable`)
- Search with hybrid ranking:
  - Lexical scoring (keywords + synonyms + trigram similarity)
  - On-device semantic ranking using quantized MiniLM ONNX model
- Event details page:
  - Full metadata, poster, organizer name, comments
  - Join/leave waitlist actions
  - Invitation accept/decline actions
  - QR dialog for public event links
- QR scanning:
  - Camera-based scan (CameraX)
  - Gallery image decode fallback
- Notifications inbox:
  - Read/confirm/decline state transitions
  - Event-specific action handling
- Profile management:
  - Editable profile fields with validation
  - Notification preference toggle
  - Auto-login toggle
  - Entrant role removal/account cleanup
- History view of event participation and statuses.

### 3) Organizer experience
- Organizer home with owned/co-organized event visibility.
- Event creation with validation:
  - Registration start/end times
  - Event time constraints
  - Capacity and waitlist constraints
  - Optional geolocation requirement
  - Public/private visibility
- Poster upload integration via FreeImageHost API (key read from Firestore config).
- Waitlist management:
  - Search entrants
  - Remove entrants
  - Invite selected entrants (private-event flow)
  - Assign selected users as co-organizers
- Lottery flows:
  - Draw winners after registration closes
  - Persist invited history and send notifications
  - Replacement draw for declined spots
  - Auto-refill helper for open spots
- Event details and comment moderation view.
- Event map (OSMDroid/OpenStreetMap):
  - Visualizes collected entrant geolocation pins
  - Supports multiple stored location points per entrant
- Organizer notifications:
  - Manual event updates
  - Targeted or broad recipient selections

### 4) Private event access model
- Private events are hidden unless entrant has access via:
  - Waitlist membership
  - Selection/invitation history
  - Explicit private waitlist invite
- Dedicated organizer flow to send private waitlist invites.
- Entrants can accept/join or decline private invites.

### 5) Admin capabilities
- Admin dashboard with tabs for users, organizers, events, and notifications.
- Entrant cleanup:
  - Remove entrant role
  - Remove entrant from event rosters/history
- Organizer cleanup:
  - Remove organizer role
  - Delete organizer-owned events and comments
- Event moderation:
  - View event details
  - Delete individual or selected events
  - Delete event comments with event cleanup
- Notification audit via collection-group queries.

### 6) Deep links and public event landing
- Public links in format: `https://wecook.app/event/{eventId}`
- Deep link entry point via `PublicEventLandingActivity`.
- Private events are blocked from public landing.

## Technical Stack

- Language: Java 11
- Platform: Android (minSdk 24, target/compileSdk 36)
- Backend: Firebase Firestore + Firebase Auth/Storage/Messaging/Analytics
- UI: AndroidX, Material Components, RecyclerView, Navigation
- QR: ZXing + CameraX
- Maps: OSMDroid + Google location services
- Search/ML: ONNX Runtime Android with on-device MiniLM embeddings

## Project Structure

- `app/src/main/java/com/example/wecookproject/`
  - Entrant screens (`User*`)
  - Organizer screens (`Organizer*`)
  - Admin screens (`Admin*`)
  - Shared helpers (`NotificationHelper`, `EntrantWaitlistManager`, `WaitlistLotteryHelper`, etc.)
- `app/src/main/java/com/example/wecookproject/model/`
  - Domain models (`User`, `Event`, `EventComment`)
- `app/src/main/assets/semantic/`
  - ONNX model + tokenizer vocab/config for semantic search
- `app/src/test/`
  - Unit tests (validators, ranking/filter logic, adapters, models)
- `app/src/androidTest/`
  - Instrumentation flow tests for entrant/organizer/admin journeys

## Firebase/Data Notes

The app expects Firestore collections/documents including:
- `users/{androidId}`
- `events/{eventId}`
- `users/{androidId}/eventHistory/{eventId}`
- `users/{androidId}/notifications/{notificationId}`
- `events/{eventId}/comments/{commentId}`
- `app_config/admin_key` with field `key`
- `app_config/freeimage_host` with field `apiKey` (for poster upload)

Place your Firebase Android config at:
- `app/google-services.json`

## Build and Run

From project root:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Run from Android Studio is also supported.

## Testing

Unit tests:

```bash
./gradlew testDebugUnitTest
```

Instrumentation tests (requires device/emulator):

```bash
./gradlew connectedDebugAndroidTest
```

The `androidTest` suite includes end-to-end flows for:
- Entrant flow (`UserFlowTest`)
- Organizer flow (`OrganizerFlowTest`)
- Admin flow (`AdminFlowTest`)
- Signup/login and cross-role behavior (`SignupFlowTest`)

## Current Known Gaps (from code comments/TODOs)

- Organizer map screen QR action is still a TODO placeholder.
- Some organizer bulk actions and redraw UX were partially staged and may need refinement.
- Some logic is activity-heavy and can be further separated into ViewModel/repository layers.

## License

No license file is currently present in this repository.
