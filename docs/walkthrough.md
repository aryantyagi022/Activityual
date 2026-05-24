# End-to-End User Walkthrough

> **Demo credentials (cloud)** — Email `aryantyagi0@gmail.com`, Password
> `Aryan1234`. Live URL: <https://d17bqzy8fgqhxi.cloudfront.net>.
---
## 1. User registers
* **UI**: open <https://d17bqzy8fgqhxi.cloudfront.net/register>, fill in
  display name / email / password, click **Create account**.
* **Request**: `POST /auth/register` with `{ email, password, displayName }`.
* **Flow**: `frontend` → CloudFront → ALB → `gateway-service`
  → `auth-service`. Password is bcrypt-hashed, the user row is inserted
  into `authdb.users`, and a JWT **access** (15 min) + **refresh** (7 d)
  pair is returned. The SvelteKit store persists them in `localStorage`
  and routes to `/dashboard`.
## 2. User creates an Activity
* **UI**: `/activities` -> **+ Add activity** -> title `Workout`, category
  `workout`, frequency `WEEKLY_3` -> **Save**.
* **Request**: `POST /activities` with the JWT in
  `Authorization: Bearer ...`.
* **Flow**: gateway validates the JWT, strips the header, injects
  `X-User-Id`, and routes to `activity-service` which inserts into
  `activitydb.activities` scoped to that user id.
Repeat for one or two more activities (e.g. *Read 20 pages, reading,
DAILY*) so the analytics and recommendation engines have multiple
series to work with.
## 3. User marks the Activity missed / done / completed
* **UI**: on the activity card click **Missed** for *Workout* on
  yesterday, then **Done** on *Read 20 pages* for today. Repeat over
  several days so the AI Coach has real history.
* **Request**: `POST /logs` with
  `{ activityId, status: "MISSED" | "DONE" | "COMPLETED", loggedAt }`.
* **Flow**: `tracking-service` writes the log into
  `trackingdb.activity_logs` **and** appends an `outbox_events` row in
  the same DB transaction (transactional outbox pattern). The
  `OutboxRelay` scheduler polls every 2 s and publishes
  `activity.logged` to the RabbitMQ topic exchange `activity.events`.
  This guarantees at-least-once delivery even if RabbitMQ is briefly
  down.
### Async fan-out (happens automatically, no user action)
| Queue         | Consumer                 | What it does                                                                                              |
| ------------- | ------------------------ | --------------------------------------------------------------------------------------------------------- |
| `analytics.q` | `analytics-service`      | Inserts `log_facts`, refreshes streak + consistency aggregates.                                            |
| `coach.q`     | `coach-service`          | Generates an embedding with `nomic-embed-text` and upserts the chunk into Chroma with `user_id` metadata.   |
| `reco.q`      | `recommendation-service` | Updates fact tables and re-runs the heuristic engine (time-of-day, day-of-week, frequency).                |
| `notif.q`     | `notification-service`   | Stores an in-app notification row in `notifdb.notifications`.                                              |
## 4. User views Analytics
* **UI**: click **Analytics** in the nav.
* **Request**: `GET /analytics/{userId}` plus `GET /analytics/{userId}/streaks`.
* **Flow**: `analytics-service` returns:
  * **Headline cards** - Total / Done / Missed / Consistency %.
  * **Per-activity consistency bar chart** (Chart.js).
  * **Streak list** - current and longest streak per activity.
The amber **Nudge banner** on the dashboard is sourced from
`recommendation-service`; it appears whenever any recommendation has
`confidence >= 0.6`.
## 5. User asks the AI Coach
* **UI**: click **Coach**, type
  *"Why do I keep missing my workout?"* -> **Ask**.
* **Request**: `POST /coach/ask` with `{ "question": "Why do I keep missing my workout?" }`.
* **Flow inside `coach-service`**:
  1. Embed the question with `nomic-embed-text`.
  2. Query **Chroma** for the **top 4** nearest log chunks **filtered
     by `user_id`** so users can never see each other's data.
  3. Build a grounded prompt:
     > *"You are Activityual, an empathetic habit coach. Use ONLY the
     > user's activity history below to answer the question...
     > USER ACTIVITY HISTORY: - {chunks}
     > USER QUESTION: {question}"*
  4. Call **Ollama** (`llama3.2:3b`, `num_predict=256`,
     `keep_alive=30m`) on the dedicated `ai` EKS node group.
  5. Return `{ answer, contextChunks }`.
* **UI**: the answer renders on the left; the **retrieved chunks** are
  shown in a side panel on the right - *this is the proof of RAG
  grounding* and the exact thing to point at during the recording.
> **Example answer (live)**: *"You logged Workout as MISSED on 3 of the
> last 7 days, all of them weekdays after 6 pm. On the 2 days you
> completed it you logged before 9 am. Try moving Workout to a morning
> slot - your weekend completion rate is 80 % vs 33 % on weekdays."*
## 6. User visits Recommendations and accepts a timing nudge
* **UI**: click **Recommendations**.
* **Request**: `GET /recommendations/{userId}` and
  `GET /recommendations/{userId}/heatmap/{activityId}`.
* **Flow**: `recommendation-service` returns ranked, explainable
  recommendations, each with a confidence score and the evidence
  string. Three rule families fire:
  * **Time-of-day** - *"You complete `Workout` 3x more often before
    9 am."*
  * **Day-of-week** - *"Your `Workout` completion is 80 % on weekends vs
    33 % weekdays."*
  * **Frequency** - *"You hit `Read 20 pages` 5 days a week - consider
    promoting it from DAILY to a streak goal."*
* **UI**: click **Accept** on the top *time-of-day* card. The card
  fades into the *accepted* list.
* **Request**: `POST /recommendations/accept`
  with `{ recommendationId, accepted: true }`.
* **Flow**: the row is marked `accepted = true` in `recodb` so we can
  later measure whether accepted nudges actually improve completion
  (closes the feedback loop without changing the public API).
## 7. User checks Notifications
* **UI**: click the bell in the top-right.
* **Request**: `GET /notifications/{userId}`; optionally
  `POST /notifications/{id}/read`.
* **Flow**: `notification-service` lists every event-driven
  notification produced in steps 3-6 (one per log, one per accepted
  recommendation, etc.) - proving end-to-end event-driven fan-out.
---
## What this scenario demonstrates (rubric mapping)
| Rubric requirement                       | Where it's shown                                                                          |
| ---------------------------------------- | ----------------------------------------------------------------------------------------- |
| User registers / logs in                 | Step 1                                                                                    |
| Creates an Activity                      | Step 2                                                                                    |
| Marks status (missed / done / completed) | Step 3                                                                                    |
| Views Analytics                          | Step 4                                                                                    |
| Asks the AI Coach a question             | Step 5                                                                                    |
| **Grounded** response based on user logs | Step 5 - `contextChunks` panel + Chroma per-user filter                                   |
| Visits the Recommendations page          | Step 6                                                                                    |
| Sees a personalised timing recommendation | Step 6 - *Time-of-day* card                                                              |
| Accepts the recommendation               | Step 6 - `POST /recommendations/accept`                                                   |
| Event-driven micro-services interaction  | Step 3 fan-out table (4 consumers off one RabbitMQ topic exchange)                        |
| Recommendation Microservice integration  | Steps 3 (consumer), 4 (nudge banner), 6 (recommendations + accept)                        |
That covers every functional requirement: **user mgmt -> CRUD -> tracking
-> async fan-out -> analytics -> grounded AI Coach -> personalised
recommendations -> event-driven notifications**.
