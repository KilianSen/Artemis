# Abgabe — Math Exercises (Equational Reasoning)

This branch adds a new **Math exercise type** to Artemis. Students prove or transform mathematical
expressions step by step in an interactive block editor; submissions are graded automatically and only
escalate to a human tutor when automatic grading is inconclusive.

This file is the entry point for reviewing the submission: a quick setup, how to run the tests, where the
code lives, and a short tour of the feature. Screenshots are in [§6 Visual overview](#6-visual-overview)
below.

To read the thesis against the code, use **[`THESIS_CODE_MAP.md`](THESIS_CODE_MAP.md)** next to this file. It
maps every thesis section, figure, algorithm, table and appendix to the files that implement it — across both
repositories — and ends with a reverse index from file back to thesis section. §3 below gives the coarse
directory layout; the map is the section-by-section version.

The full narrative walkthrough is the instructor documentation page
`documentation/docs/instructor/exercises/math-exercise.mdx`. It is Docusaurus **MDX** — it imports a React
image component and its screenshots, so GitHub shows it as raw source rather than rendering it. To read it
properly, start the documentation site and open
*Instructor → Exercises → Math Exercise* (it serves on <http://localhost:3000>):

```bash
cd documentation
corepack enable      # one-time: activates the pinned pnpm
pnpm install
pnpm start
```

## Branch basis

This work is built on Artemis's **`develop`** branch rather than a release tag, so that it can be merged
upstream without a rebase onto a moving target. That choice has a cost worth stating plainly: `develop` is the
active integration branch, it carries a number of known bugs at any given time, and two large UI migrations
are currently in flight across it — Bootstrap components are being replaced by PrimeNG, and the Angular
codebase is moving from legacy decorators to the signal-based APIs.

Expect the consequences while reviewing: rough edges in **shared components** that the math feature only
consumes, and **visual inconsistencies** where migrated and not-yet-migrated UI sit side by side. Unless a
problem is inside the math exercise code itself, it is most likely inherited from `develop` rather than
introduced by this submission.

---

## 1. Quick setup

Follow this section top to bottom and you end up with a running Artemis dev instance that has **math exercises
enabled and both lightweight Regate provers attached**, covering every goal mode.

It is written for **Linux** and assumes **nothing is installed yet** — every tool is set up below. Budget about
30 minutes (mostly downloads), **~20 GB free disk**, and **16 GB RAM** (8 GB works, but the client build is
slow). All commands are run in a terminal; `$HOME/artemis-ba` is used as the working directory, but any
directory will do.

### Step 0 — Install the prerequisites

Skip whatever you already have. The `apt` commands below are for **Debian/Ubuntu**; on other distributions
use the equivalent package manager.

**Docker** — runs the database and the grading backends. Install it as described at
<https://docs.docker.com/engine/install/>.

**The rest of the tools:**

```bash
sudo apt update
sudo apt install -y git curl unzip zip
```

Then allow your user to use Docker without `sudo`, and apply it to the current shell:

```bash
sudo usermod -aG docker "$USER"
newgrp docker
```

> **This group step is the most common stumbling block.** Without it every Docker command fails with
> `permission denied while trying to connect to the Docker daemon socket`. If `newgrp docker` does not take
> effect, log out and back in.

**Java 25** — via SDKMAN, which installs into your home directory and needs no root:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-tem
```

**Node 24 and pnpm** — via nvm, likewise no root (check the nvm README for the current installer version):

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source "$HOME/.nvm/nvm.sh"
nvm install 24
corepack enable       # activates the exact pnpm version pinned in package.json
```

**Verify** — every line should print a version, and the Docker line must succeed *without* `sudo`:

```bash
docker run --rm hello-world > /dev/null && echo "docker ok"
docker compose version   # must be v2.x — v1 (`docker-compose`) cannot read these compose files
java -version    # 25.x
node -v          # v24.x
git --version
```

If you opened a new terminal since installing, re-run the two `source` lines above (SDKMAN and nvm add them to
your shell profile for future sessions).

### Step 1 — Get both repositories, side by side

The grading backends live in the separate **Regate** project (**not part of this hand-in**). Clone both into
the same parent directory — that is the layout the Docker setup assumes by default:

```bash
mkdir -p "$HOME/artemis-ba" && cd "$HOME/artemis-ba"
git clone --branch Abgabe https://github.com/KilianSen/Artemis.git Artemis
git clone https://github.com/KilianSen/Regate.git Regate
cd Artemis
```

giving you:

```
~/artemis-ba
├── Artemis/     # this repository — all remaining commands run from here
└── Regate/      # the grading backends
```

A different location works too — you then pass `REGATE_PATH=/path/to/Regate` in step 3.

### Step 2 — Start the database

The `dev` profile expects MySQL on `localhost:3306` (database `Artemis`, user `root`, empty password). Run it
from the `Artemis` directory — `--env-file` is required here, as `docker/mysql.yml` deliberately defines no
fallback for `MYSQL_IMAGE`:

```bash
docker compose --env-file .env -f docker/mysql.yml up -d
```

Give it a few seconds, then confirm it reports `(healthy)`:

```bash
docker ps --filter name=artemis-mysql
```

Liquibase creates the schema (including the `math_*` tables) when Artemis first starts in step 4.

### Step 3 — Start the Regate grading backends

Also from the `Artemis` directory — [`docker/regate.yml`](docker/regate.yml) builds the backends straight from
the Regate checkout you cloned in step 1 (Regate publishes no images). By default it starts only the two light
backends, which together cover all three goal modes and are the two the live tests exercise. The first build
takes a few minutes:

```bash
docker compose --env-file .env -f docker/regate.yml up -d --build
```

Check they are up:

```bash
curl -s localhost:8000/health; echo; curl -s localhost:8003/health
```

which prints exactly:

```json
{"status": "ok", "backend": "eggregate", "version": "0.1.0", "protocol": "1.1"}
{"status": "ok", "backend": "cvc5regate", "version": "0.1.0", "protocol": "1.1", "cvc5": true, "carcara": false}
```

`"cvc5": true` is the one to watch — it means the solver binary is present inside the container. (`"carcara":
false` is expected and fine: it only marks the optional independent proof re-checker.)

Optionally, prove the graders work **before** involving Artemis at all, by grading the worked example from the
thesis appendix directly:

```bash
curl -s localhost:8000/grade -H 'content-type: application/json' \
  -d @../Regate/examples/appendix-b.json \
  | python3 -c "import json,sys; r=json.load(sys.stdin); print(r['outcome'], r['score'], r['certified'])"
```

A correct setup prints `proven_equal 75 True`. If this works but grading in the UI does not, the problem is in
Artemis's configuration, not in the backends.

| Backend | Port | Grades | Image |
| --- | --- | --- | --- |
| `eggregate` (egglog e-graph) | 8000 | Transformation, Equation | small |
| `cvc5regate` (cvc5 SMT induction) | 8003 | Induction | ~60 MB |

Notes:

- If your Regate checkout is **not** next to this repository, pass its path:
  `REGATE_PATH=/path/to/Regate docker compose --env-file .env -f docker/regate.yml up -d --build`.
- `cvc5regate` is pinned to `linux/amd64`; on Apple Silicon it runs under emulation (slower, but works).
- The two large formal provers are deliberately **skipped** by default: `leanregate` (~9 GB) and `coqregate`
  (~1.5 GB). Add them with the `full` Compose profile if you want to exercise Lean-certified induction — the
  `dev` profile already points at them on `:8001` and `:8002`:

  ```bash
  docker compose --env-file .env -f docker/regate.yml --profile full up -d --build
  ```

- Leaving any of them down is harmless — see *Graceful degradation* below.

### Step 4 — Start Artemis

The Spring profiles must be passed explicitly — they select the configuration files that hold the admin
account, the module toggles, and the Regate URLs. This is the same list the project's IntelliJ run
configuration *Artemis (Server, Dev, BuildAgent & LocalCI)* uses:

```bash
./gradlew bootRun --args='--spring.profiles.active=artemis,localci,localvc,scheduling,buildagent,core,dev,local'
```

The first run downloads the Gradle and npm dependencies and builds the Angular client — expect **10–20
minutes**. It is ready when the log prints:

```
Started ArtemisApp in 174.358 seconds
	'Artemis' is running! Access URLs:
	Local: 		http://localhost:8080/
```

Leave this terminal open; the server runs in the foreground on **http://localhost:8080**.

For faster client iteration you can split the two, in two terminals:

```bash
./gradlew bootRun -x webapp --args='--spring.profiles.active=artemis,localci,localvc,scheduling,buildagent,core,dev,local'
pnpm install && pnpm start     # Angular dev server with hot reload on http://localhost:9000
```

> **Do not drop the `--args`.** A bare `./gradlew bootRun` activates only the `dev` profile, which does not
> load `application-artemis.yml` (admin account, repository paths). Note also that `dev` must come **after**
> `core` in the list: both define `artemis.math.enabled`, the later profile wins, and only `dev` sets it to
> `true`.

### Step 5 — Log in and try it out

Open **http://localhost:8080** (or **http://localhost:9000** with the split setup) and log in with the
built-in administrator account, created automatically on first startup:

```
username: artemis_admin
password: artemis_admin
```

Then walk through the **[two-minute tour](#5-a-two-minute-tour)** at the end of this file: create a course,
create a math exercise from a starter template, solve it as a student, and watch it get graded.

To exercise the **Regate** path specifically, pick a remote backend in the authoring form's **Graders**
multi-select — `eggregate` for a Transformation or Equation problem, `cvc5regate` for an Induction problem.
These grade asynchronously: the submission is accepted immediately and the score arrives over a websocket a
moment later.

### Step 5b — Provision a demo course (optional, but the fastest way to see everything)

Clicking through the feature by hand covers one exercise at a time. This script fills a course with a
broad, deliberately varied catalogue and then answers it as three different students, so the graded
states exist before you look at anything.

**Prerequisite — activate the `e2e` seed.** The script provisions into course `9018` (*E2E Exercise
Participation Course*) and answers as `artemis_test_user_1..3`. Both come from the Liquibase **`e2e`**
seed, which the `dev` profile does *not* switch on — `application-dev.yml` sets `liquibase.contexts: dev`,
and every changeset in `20260304120000_e2e_seed_data.xml` is gated on `context="e2e"`. So start the server
from step 4 with the context added:

```bash
SPRING_LIQUIBASE_CONTEXTS="dev,e2e" ./gradlew bootRun --args='--spring.profiles.active=artemis,localci,localvc,scheduling,buildagent,core,dev,local'
```

It has to run against an **empty database**. The user seed is skipped when an `artemis_admin` row already
exists, so adding the variable to an instance that has booted before creates the courses but *no*
students — the exercises are then provisioned and the submissions all fail. If you have started Artemis
already, reset the database first and boot again with the variable above:

```bash
docker compose --env-file .env -f docker/mysql.yml down -v
docker compose --env-file .env -f docker/mysql.yml up -d
```

Then run the script. Pass `BASE_URL` when the server serves the client itself on `8080` — the script
defaults to `9000`, the dev-server port of the split setup in step 4:

```bash
BASE_URL=http://localhost:8080 node supporting_scripts/math-demo/provision-math-demo.js
```

It creates **52 math exercises** in course `9018` covering all three goal modes, every grader — the
catalogue is weighted towards the Regate backends, with `eggregate` on most transformation and equation
entries and `cvc5regate` on the induction section — and the editor options (partial credit, AC
normalisation, manual derivation, restricted rule palette, verification off), then submits answers
landing in each outcome:

| Outcome | Roughly | What it demonstrates |
| --- | --- | --- |
| `100` correct | 29 | A derivation that reaches the goal |
| `0` wrong / empty | 14 | An invalid step, and an untouched submission |
| partial (e.g. 50%, 66.7%) | 7 | Distance-based credit for an unfinished chain |
| awaiting review (`null` score) | 2 | Automatic grading inconclusive → routed to a tutor |

Options: `--course <id>` targets a different course, `--no-submissions` creates the exercises only.
`--course` only changes where the exercises go: the three student logins are fixed in the script, so a
course of your own still needs `artemis_test_user_1..3` enrolled in it, or you want `--no-submissions`.
It needs the Regate backends from step 3 for the Regate-graded entries; any backend that is not
running simply routes its exercise to manual review, which is one of the states above anyway.

Each run adds a **fresh batch** rather than updating in place, so run it once unless you want duplicates.
Afterwards, browse as a student at `/courses/9018/exercises`, or as a tutor at
`/course-management/9018/assessment-dashboard` to see the submissions waiting for assessment.

### Step 5c — Provision the FPV course (optional)

Where step 5b shows the feature's *range*, this one shows it against a **real course's exercises**: the
MiniOCaml set from *Functional Programming and Verification* (artemis.tum.de course 443, weeks 11–12).

```bash
BASE_URL=http://localhost:8080 node supporting_scripts/math-demo/provision-fpv-course.js
```

It creates its own course — titled *Abgabe FPV Equational Reasoning MiniOCaml* so it sorts to the **top**
of the course selection, ahead of the `E2E …` seed courses — reusing course 9018's groups so the same
`artemis_test_user_*` logins are enrolled. It therefore needs the same `e2e` seed as step 5b.

Three of the ten FPV exercises port over, because the math exercise type grades *equational* reasoning:
rewrite chains and structural induction over ℕ, lists and binary trees. Each becomes two problems — the
accumulator-generalised lemma by induction, then the equation closing it back to the original claim:

| FPV exercise | Lemma, by induction (cvc5regate) | Closing step (path checker) |
| --- | --- | --- |
| 17215 What The Fact | `fact_aux x n = x · fact n` over ℕ | `1 · fact n = fact n` |
| 17216 Arithmetic 101 | `sum l a = a + summa l` over a list | `0 + summa l = summa l` |
| 17217 Counting Nodes | `aux t a = a + nodes t` over a tree (two IHs) | `0 + nodes t = nodes t` |

All three lemmas come back `proven_equal` and **certified** — one per induction datatype. The remaining
seven exercises are big-step operational semantics, termination proofs, a semantics extension, or
multiple-choice quizzes; none is a term-rewriting problem, and the script prints the list with reasons on
every run. The recursive definitions (`fact`/`fact_aux`, `summa`/`sum`, `nodes`/`aux`) ship from
`ApplyBlockDefinition`, so no per-problem function authoring is needed.

Same options as step 5b: `--course <id>` provisions into an existing course instead of creating one,
`--no-submissions` creates the exercises only.

### Step 6 — Shutting down

**Artemis itself** runs in the foreground: press `Ctrl+C` in the terminal running `./gradlew bootRun` (and in
the `pnpm start` terminal, if you used the split setup). That frees ports `8080` and `9000`. A lingering Gradle
daemon can be stopped with `./gradlew --stop`.

**The containers**, from the `Artemis` directory:

```bash
docker compose --env-file .env -f docker/regate.yml down    # grading backends
docker compose --env-file .env -f docker/mysql.yml down     # database (data is kept)
```

Both are safe to re-run; `up -d` afterwards brings you back to the same state.

Notes:

- If you started the backends with `--profile full`, pass the same flag to `down` so the `leanregate` and
  `coqregate` containers are removed as well:
  `docker compose --env-file .env -f docker/regate.yml --profile full down`.
- `down` keeps the database volume. To **reset the database completely** (wipes all courses, exercises, and
  submissions — Liquibase recreates the schema on the next boot):

  ```bash
  docker compose --env-file .env -f docker/mysql.yml down -v
  ```

- To also reclaim the disk used by the locally built backend images, add `--rmi local` to the `regate.yml`
  `down` — worth it after a `--profile full` run, which builds ~11 GB of images.
- Confirm nothing is left behind with `docker ps --filter name=artemis-`.

### What the `dev` profile wires for you

Set in [`src/main/resources/config/application-dev.yml`](src/main/resources/config/application-dev.yml) — no
environment variables required:

| Property | Value |
| --- | --- |
| `artemis.math.enabled` | `true` (the module is **off** by default outside `dev`) |
| `artemis.regate.eggregate.url` | `http://localhost:8000` |
| `artemis.regate.leanregate.url` | `http://localhost:8001` |
| `artemis.regate.coqregate.url` | `http://localhost:8002` |
| `artemis.regate.cvc5regate.url` | `http://localhost:8003` |

To use different ports or a remote host, override them per backend (these also work outside the `dev`
profile, where the URLs are empty by default):

```bash
export ARTEMIS_REGATE_EGGREGATE_URL=http://localhost:8000
export ARTEMIS_REGATE_CVC5REGATE_URL=http://localhost:8003
# leanregate / coqregate analogous
```

### Graders and graceful degradation

Math exercises are graded by either:

- the **in-process Path checker** (`PATH_CHECKER`) — needs **nothing extra**, no backend, no Docker; or
- one of four **remote Regate provers** (`eggregate`, `leanregate`, `coqregate`, `cvc5regate`) — reached over
  HTTP, graded asynchronously, with the result pushed to the student over a websocket.

Skipping steps 2–3 entirely still leaves a working app: a submission routed to a remote grader whose backend
is unavailable (or whose URL is unset) is **escalated to manual tutor review** rather than failed — this is the
intended behaviour and is what the review/assessment flow demonstrates.

### Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `permission denied … /var/run/docker.sock` | Your user is not in the `docker` group. Re-run the `usermod` step in step 0, then `newgrp docker` or log out and back in. |
| `unset variable MYSQL_IMAGE` or similar on `docker compose` | The `--env-file .env` flag is missing. `docker/mysql.yml` defines no fallback for it. (`docker/regate.yml` does default `REGATE_PATH` and the port variables, so it parses without the flag — pass it anyway for consistency.) |
| Build fails with `../../Regate: no such file or directory` | The Regate checkout is not next to the Artemis one. Re-check step 1, or pass `REGATE_PATH=/path/to/Regate`. |
| `/health` prints `"cvc5": false` | The container is missing the solver binary — rebuild with `--build`. Grading still answers, but induction degrades to `unknown` (review) instead of certifying. |
| A backend is missing from `docker ps` right after starting | It crash-looped. Check `docker logs artemis-cvc5regate`; a `ModuleNotFoundError` means the Regate checkout's Dockerfile does not copy every module `grade.py` imports. |
| Another `docker compose` command warns about "orphan containers" | Every compose file here shares `COMPOSE_PROJECT_NAME=artemis`, so each one sees the others' containers as orphans. The warning is harmless — but never add `--remove-orphans`, which would delete the backends and the database. |
| The E2E suite's escalation test fails | It requires the remote backend to be **down** (it asserts the submission escalates to review). Stop the backends before running it: `docker compose --env-file .env -f docker/regate.yml stop`. |
| Port `3306`, `8000`, `8003`, or `8080` already in use | Another service (often a local MySQL) holds it. Stop it, or change the published port — `EGGREGATE_PORT`/`CVC5REGATE_PORT` for the backends, and the matching `ARTEMIS_REGATE_*_URL` so Artemis follows. |
| Server exits with an unresolvable-placeholder or missing-property error | The profile list was dropped from `bootRun`. See the warning in step 4. |
| Login rejects `artemis_admin` | The `artemis` profile was not active on the **first** startup, so the admin was never created. Stop the server, `docker compose --env-file .env -f docker/mysql.yml down -v`, and start again with the full profile list. |
| The math exercise type is missing from Course Management | `artemis.math.enabled` is not `true` — the `dev` profile must come *after* `core` in the profile list. |
| A submission stays *"awaiting tutor review"* forever | Its grader is a backend that is not running. Check `docker ps` and `curl localhost:8000/health`; this escalation is by design, not a crash. |
| The client build runs out of memory | Give the machine more RAM/swap, or use the split setup in step 4 so the Angular build runs on its own. |
| `provision-math-demo.js` fails, or course `9018` does not exist | The Liquibase `e2e` seed was not applied. It is off under the plain `dev` profile and only takes effect on an empty database — see the prerequisite in step 5b. |
| `provision-math-demo.js` creates the exercises but every submission fails | The `e2e` context was added to a database that had already booted once, so the course seed ran but the user seed was skipped and `artemis_test_user_*` do not exist. Reset the database and boot again with the context — step 5b. |

---

## 2. Running the tests

### Server (requires Docker — PostgreSQL via Testcontainers)

```bash
# The whole math test tree
./gradlew test --tests "de.tum.cit.aet.artemis.math.*" -x webapp

# Or the key classes individually
./gradlew test --tests "de.tum.cit.aet.artemis.math.MathSubmissionIntegrationTest" -x webapp
./gradlew test --tests "de.tum.cit.aet.artemis.math.MathExerciseIntegrationTest" -x webapp
./gradlew test --tests "de.tum.cit.aet.artemis.math.MathGradingRecoveryServiceTest" -x webapp
```

The `Live*` / `MathRegateAsyncGradingLiveTest` classes are **env-guarded**: they only run when a Regate
backend URL is provided (e.g. `REGATE_LIVE_URL` / the `ARTEMIS_REGATE_*_URL` variables) and are otherwise
skipped — so the default suite runs without any external backend.

### Client (Vitest)

```bash
pnpm run vitest:run "app/math/"     # all math client specs
```

### End-to-end (Playwright)

```bash
./run-e2e-tests-local-fast.sh --filter "Math"
```

This starts its own Postgres, server and client (killing anything on ports 8080/9000), seeds the database via
the `e2e` Liquibase context, and runs the five math tests across the three specs below.

Two things to know before running it:

- It calls `playwright install --with-deps`, which needs **root**. If sudo is unavailable it aborts with
  `Failed to install browsers`. Install the browser once yourself beforehand, then the run proceeds:
  `cd src/test/playwright && pnpm exec playwright install chromium`.
- **Stop the Regate backends first** — one spec asserts that a submission escalates to tutor review *because*
  the remote grader is unreachable, and it fails if `eggregate` is actually running:
  `docker compose --env-file .env -f docker/regate.yml stop`.

Specs: `MathExerciseParticipation`, `MathExerciseAssessment`, `MathExerciseManagement`
(`src/test/playwright/e2e/exercise/math/`).

---

## 3. Where the code lives

| Area | Path |
| --- | --- |
| Server (domain, graders, Regate client, services, REST, repositories, DTOs) | `src/main/java/de/tum/cit/aet/artemis/math/` |
| Client — participation | `src/main/webapp/app/math/participate/` |
| Client — authoring | `src/main/webapp/app/math/manage/update/` |
| Client — assessment | `src/main/webapp/app/math/manage/assess/` |
| Client — detail / list | `src/main/webapp/app/math/manage/detail/`, `.../manage/exercise/` |
| Database migrations | `src/main/resources/config/liquibase/changelog/` (`math_*` tables) |
| Instructor documentation | `documentation/docs/instructor/exercises/math-exercise.mdx` |
| E2E tests | `src/test/playwright/e2e/exercise/math/` |

---

## 4. What was implemented

- **Exercises with multiple problems.** Goal modes: **Transformation** (rewrite a start expression into a
  target), **Equation** (prove both sides equal), and **Induction** (prove a statement over a variable).
- **Interactive block editor** for building derivations, with **hints** (suggested rules), a **manual step**
  mode, and **quiz-style navigation** across problems.
- **Grading:** the in-process rewrite-chain engine plus four remote Regate provers, on fast/slow lanes.
- **Durable asynchronous grading:** the grading job is persisted so a server restart mid-grade recovers;
  the result is pushed to the student over a websocket.
- **Escalation to manual review:** an inconclusive or failed automatic verdict is never scored zero — it is
  sent to a tutor. The student sees an *"awaiting tutor review"* state, and instructors see a review-queue
  count.
- **Tutor assessment workflow:** a review queue on the standard assessment dashboard, **soft locking** (no
  two tutors on the same submission), draft vs. submit, and cancel.
- **Complaint-response loop:** a student can complain about an assessment; a different tutor or an instructor
  reviews and accepts/rejects it, revising the score.
- **Curated starter templates** in the authoring form so instructors can begin from a basic
  equational-reasoning problem instead of a blank canvas.
- **Tests:** Java integration + unit tests, client Vitest specs, Playwright E2E, and env-guarded live tests
  against the real Regate backends.

---

## 5. A two-minute tour

1. **Instructor:** create a Math exercise, pick a **starter template** (e.g. *Left identity of addition*),
   and save.
2. **Student:** open the exercise, select the `Identity (left)` rule and apply it to the `0 + x` expression to
   reach the goal `x`, then **submit** — with the in-process grader the score appears immediately.
3. **Escalation (optional):** set a problem's grader to a remote backend that isn't running, submit, and
   watch it become *"awaiting tutor review"*; then pick it up from **Assessment → the exercise's dashboard**,
   score it, and submit.

---

## 6. Visual overview

The whole feature, in the order a course actually uses it. Same images as the instructor documentation page.

### Instructor — authoring

Creating a math exercise from course management:

![Creating a math exercise](documentation/docs/instructor/exercises/assets/math/create-exercise.png)

The authoring form — exercise-level settings, then one card per problem:

![Math exercise authoring form](documentation/docs/instructor/exercises/assets/math/authoring-form.png)

Per-problem configuration: goal mode, expressions, allowed rules, and the grading options:

![Per-problem block editor](documentation/docs/instructor/exercises/assets/math/problem-editor.png)

Pre-filling a problem from a curated starter template:

![Starter templates](documentation/docs/instructor/exercises/assets/math/starter-templates.png)

Picking graders. Backends that cannot grade the chosen goal mode are disabled — here the induction-only
provers are greyed out for a transformation problem:

![Grader multi-select](documentation/docs/instructor/exercises/assets/math/grader-selection.png)

For an induction problem the formal provers become selectable instead:

![Graders for induction](documentation/docs/instructor/exercises/assets/math/grader-induction.png)

An optional slower certifier can double-check the fast preliminary verdict:

![Optional certifier](documentation/docs/instructor/exercises/assets/math/grader-certifier.png)

Listing the Path checker behind a stronger backend makes it a fallback, and the form says so:

![Path-checker fallback warning](documentation/docs/instructor/exercises/assets/math/grader-fallback-warning.png)

The exercise detail view with statistics and the review-queue count:

![Math exercise detail](documentation/docs/instructor/exercises/assets/math/exercise-detail.png)

### Student — participation

The workspace: problem navigation on the left, goal at the top, derivation below:

![Student workspace](documentation/docs/instructor/exercises/assets/math/student-multi-problem.png)

The block editor itself — blocks, the searchable rule palette, and the interactive expression canvas:

![Student block editor](documentation/docs/instructor/exercises/assets/math/student-editor.png)

Hints: up to three next rules, ranked by progress toward the goal:

![Suggested next rules](documentation/docs/instructor/exercises/assets/math/student-hints.png)

A derivation that reaches the goal:

![Goal reached](documentation/docs/instructor/exercises/assets/math/student-goal-reached.png)

In manual step mode the student writes the result expression instead of Artemis applying the rule:

![Manual step](documentation/docs/instructor/exercises/assets/math/student-manual.png)

An induction problem, with base case and inductive step:

![Induction problem](documentation/docs/instructor/exercises/assets/math/student-induction.png)

A graded submission:

![Graded submission](documentation/docs/instructor/exercises/assets/math/student-graded.png)

When automatic grading is inconclusive or a backend is unavailable, the submission is escalated rather than
scored zero:

![Awaiting tutor review](documentation/docs/instructor/exercises/assets/math/student-under-review.png)

### Tutor — assessment

Escalated submissions appear on the standard assessment dashboard:

![Assessment dashboard](documentation/docs/instructor/exercises/assets/math/assessment-dashboard.png)

Assessing a locked submission — soft lock, manual score, and feedback:

![Assessing a submission](documentation/docs/instructor/exercises/assets/math/assessment-view.png)

Per-problem review: the goal, the student's derivation, and the sample solution side by side:

![Per-problem derivation review](documentation/docs/instructor/exercises/assets/math/assessment-derivation.png)

---

## 7. Contribution at a glance

The card below is generated from the git history (commits, files, and lines authored by KilianSen) by
[`KilianSen/gitproof`](https://github.com/KilianSen/gitproof) and refreshed on every push to this branch by
[`.github/workflows/contribution-badge.yml`](.github/workflows/contribution-badge.yml).

![Contribution card for KilianSen](contribution.svg)
