# End-to-end walkthrough
This is the full path a user takes through Activityual, from sign-up to the
AI Coach answer, with the service that handles each call. If you want to
verify it against the deployed app, the demo credentials are
`aryantyagi0@gmail.com` / `Aryan1234` on https://d17bqzy8fgqhxi.cloudfront.net.
## 1. Sign up
Open `/register`, fill in display name, email and password, hit Create
account. The call goes `frontend -> CloudFront -> ALB -> gateway-service ->
auth-service`. The password is bcrypt-hashed, the row goes into `authdb.users`,
and the service returns an access token (15 min) plus a refresh token (7 d).
The SvelteKit auth store puts both in `localStorage` and redirects to
`/dashboard`.
## 2. Create an activity
Go to `/activities`, click "+ Add activity", and add something like
"Workout / workout / WEEKLY_3". The frontend issues `POST /activities` with
the access token in the `Authorization` header. The gateway validates the
JWT, strips the header, and injects `X-User-Id` for the downstream service.
`activity-service` writes the row into `activitydb.activities` scoped to that
user id.
It's worth adding a second activity here (something `DAILY` like
"Read 20 pages") so the analytics and recommendation engines have multiple
series to chew on.
## 3. Mark progress
On the activity card pick Missed, Done or Completed for whatever date you
want. The frontend issues
`POST /logs { activityId, status, loggedAt }`. The interesting bit here is
that `tracking-service` writes the log row and an `outbox_events` row inside
a single DB transaction. A scheduled relay polls the outbox table every two
seconds and publishes `activity.logged` to the `activity.events` topic
exchange in RabbitMQ. If RabbitMQ is briefly unavailable, nothing is lost:
the outbox row just sits there until the relay catches up.
Four queues are bound to that exchange, one per consumer:
- `analytics.q` -> `analytics-service` inserts a fact row and refreshes the
  streak / consistency aggregates.
- `coach.q` -> `coach-service` generates an embedding with `nomic-embed-text`
  and upserts the chunk into Chroma with `user_id` as metadata. That's how
  the Coach later answers questions about *this* user without leaking
  anyone else's data.
- `reco.q` -> `recommendation-service` updates its facts and re-runs the
  heuristic engine (time-of-day, day-of-week, frequency).
- `notif.q` -> `notification-service` writes an in-app notification.
Consumers de-dupe on event id so at-least-once delivery is fine.
## 4. Look at the analytics
Click Analytics in the nav. The page hits
`GET /analytics/{userId}` and `GET /analytics/{userId}/streaks` on
`analytics-service`. You get headline cards (Total, Done, Missed,
Consistency %), a Chart.js bar chart with per-activity consistency, and a
streak list with current and longest streaks.
The amber Nudge banner on the dashboard, by the way, comes from
`recommendation-service`. It only shows up when a recommendation has
`confidence >= 0.6`, otherwise it stays hidden.
## 5. Ask the Coach
Open `/coach` and type a question, e.g. "Why do I keep missing my workout?".
The frontend POSTs to `/coach/ask`. Inside `coach-service` five things
happen in order:
1. Embed the question with `nomic-embed-text`.
2. Query Chroma for the top 4 nearest log chunks, *filtered* by `user_id`.
   The per-user filter is the only thing standing between users and each
   other's data.
3. Stitch the chunks into a prompt: "You are Activityual, an empathetic
   habit coach. Use ONLY the user's activity history below ..."
4. Call Ollama (`llama3.2:3b`, `num_predict=256`, `keep_alive=30m`) on the
   tainted `ai` EKS node group.
5. Return `{ answer, contextChunks }`.
The chunks are shown in the right-hand side panel in the UI. That's the
honest way to demonstrate the answer is grounded in real logs - the user
can read the same evidence the model did.
A typical answer in the wild looks something like:
> "You logged Workout as MISSED on 3 of the last 7 days, all of them
> weekdays after 6 pm. On the 2 days you completed it, you logged before
> 9 am. Try moving Workout to a morning slot - your weekend completion is
> ~80% vs ~33% on weekdays."
If Ollama is cold (first request after the pod is started) the call can
take 30-40 s. Once the model is loaded it stays resident for 30 minutes
thanks to `keep_alive`, and subsequent calls are noticeably faster.
## 6. Take a recommendation
Open `/recommendations`. The frontend hits
`GET /recommendations/{userId}` and, optionally,
`GET /recommendations/{userId}/heatmap/{activityId}` for the heat-map view.
`recommendation-service` returns a ranked list of cards, each with a
confidence and a short evidence sentence. Three rule families produce them:
- **Time of day.** "You complete Workout 3x more often before 9 am."
- **Day of week.** "Workout completion is 80% on weekends vs 33% weekdays."
- **Frequency.** "You hit Read 20 pages 5 days a week - consider promoting
  it from DAILY to a streak goal."
Click Accept on the top card. The frontend posts
`POST /recommendations/accept { recommendationId, accepted: true }` and the
row is marked accepted in `recodb`. The reason that flag is there is so I
can later measure whether accepted nudges actually improve completion
without changing the public API.
## 7. Notifications
The bell icon hits `GET /notifications/{userId}` and renders a list with an
unread badge. Every step above produced a notification on the back end, so
by the time you get here you'll already have a handful: one per log, one
per accepted recommendation. `POST /notifications/{id}/read` marks them
read.
