# ProductivityTools.Fitness.Workouts.Api (fitness-workouts-api)

REST API service for fitness tracking, workout management, and training schedule logging. Built with **Spring Boot**, **Java 21**, **Spring Data JPA**, **Flyway**, and **PostgreSQL**.

---

## 🏗️ Architecture & Infrastructure

* **Google Cloud Platform (GCP)**:
  * **Compute Engine VM (`fitness-workouts-api-vm`)**: Hosts the Spring Boot application running as a `systemd` service (`fitness-workouts-api.service`).
  * **Cloud SQL Production (`ptfitness`)**: Private instance using **Private Service Connect (PSC)** (`10.10.0.2:5432`, database `ptfitness-workouts-api`).
  * **Cloud SQL Development (`ptfitness-dev`)**: Development/testing instance with **Public IP** enabled for seamless local connections via **Cloud SQL Auth Proxy**.
* **CI/CD Deployment**:
  * Automated GitHub Actions matrix workflow deploying simultaneously to self-hosted runners (`fitness-workouts-api-vm` and local servers).

---

## 💻 Local Development & Connecting to GCP Cloud SQL

You can connect your local development environment to Cloud SQL in GCP using one of two methods:

### Option A: Cloud SQL Auth Proxy (Connecting to `pt-fitness-dev`)

Since `pt-fitness-dev` has Public IP enabled, you can connect directly using Google's official Cloud SQL Proxy:

1. **Install Cloud SQL Proxy**:
   ```bash
   mkdir -p ~/.local/bin
   curl -o ~/.local/bin/cloud-sql-proxy https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.14.0/cloud-sql-proxy.linux.amd64
   chmod +x ~/.local/bin/cloud-sql-proxy
   ```

2. **Authenticate with Google Cloud**:
   ```bash
   gcloud auth application-default login
   ```

3. **Start the Proxy**:
   ```bash
   cloud-sql-proxy pwujczyk-pt:europe-central2:ptfitness-dev --port 5432
   ```

4. **Run the Spring Boot Application**:
   ```bash
   ./gradlew bootRun
   ```

---

### Option B: SSH Tunnel (Connecting to `ptfitness` PSC instance)

To connect to the private production instance `ptfitness`, open an SSH tunnel through `fitness-vm`:

```bash
gcloud compute ssh fitness-vm --zone=europe-central2-a --project=pwujczyk-pt -- -L 5432:10.10.0.2:5432 -N
```

Then start your Spring Boot application locally (`./gradlew bootRun`).

---

## 🗄️ Database Schema (Multi-User)

The application supports multi-tenancy and multiple users:

* **`fitness_user`**: Application user profiles and preferences.
* **`exercise`**: Catalogue containing both:
  * **System Exercises (`is_system = TRUE, user_id = NULL`)**: Global, built-in, read-only exercises available to everyone (e.g. Deadlift, Bench Press, Squat).
  * **Custom Exercises (`is_system = FALSE, user_id = <USER_ID>`)**: User-created exercises editable only by their creator.
* **`workout`**: Workout session headers (duration, status, timestamps, notes).
* **`workout_exercise`**: Exercises included in a workout session with order index, rest timer, and notes.
* **`workout_set`**: Sets with weight (kg), repetitions, RPE, set type (`NORMAL`, `WARMUP`, `DROPSET`, `FAILURE`), and completion checkmark.
* **`workout_template` / `workout_template_exercise`**: Routines and workout plans.

Database migrations are managed automatically by **Flyway** under `src/main/resources/db/migration/`.

---

## 🚀 Infrastructure as Code (Terraform)

Terraform configurations are located in `terraform/`:

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

Key resources managed:
* VPC network (`fitness`) and subnetwork in Warsaw (`europe-central2`).
* Network Firewall rules (HTTP port 80 and SSH via IAP).
* Compute Engine VM (`fitness-vm`) with automated GitHub Actions runner setup.
* Cloud SQL PostgreSQL instance with Private Service Connect (PSC) endpoint and Public IP for Proxy access.


## Debug

## Debug
Check if github action is working on the ububtu

```
sudo systemctl status "actions.runner.*"
sudo systemctl status fitness-workouts-api.service


logs
sudo journalctl -u fitness-workouts-api.service -n 100 --no-pager

sudo systemctl restart fitness-workouts-api.service
```


### Posgresql

```
systemctl status postgresql
pg_lsclusters
sudo ufw allow 5432/tcp
```

### Password
Password that application uses is written in `/opt/fitness-workouts-api/fitness-workouts-api.env`
It is created by github action
```
    - name: Configure environment variables and systemd service
      env:
        SERVER_PORT: ${{ matrix.server-port }}
        DB_URL: ${{ matrix.db-url }}
        DB_USER: ${{ matrix.db-user }}
        DB_PASS: ${{ secrets.DB_PASSWORD }}
```

Password on the Cloud is taken from the terraform.tfvars

### Cloud
Resources that were needed:
- cloud sql with PSC connection enabled
- private service connect - I needed to provide url for the sql (projects/oa26f940d25c4019ap-tp/regions/europe-central2/serviceAttachments/a-e1cc43bf2b10-psc-service-attachment-4a053db712101d02
) and the subnetwork 

For DB public IP was enabled ot be able to use cloud-sql-proxy




## Debug

### Proxy to databsae - does not work
database:
curl -o /usr/local/google/home/pwujczyk/cloud-sql-proxy https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.14.0/cloud-sql-proxy.linux.amd64
chmod +x /usr/local/google/home/pwujczyk/cloud-sql-proxy

./cloud-sql-proxy pwujczyk-pt:europe-central2:ptfitness-dev --port 5432

gcloud auth application-default login

### Gradle

````
export DB_PASSWORD="dfsafafa"
./gradlew bootrun^
````


## Endpoints

* `GET /api/exercise/list`: List available exercises (system + user's custom exercises).
* `GET /api/workout/list`: List all workouts for current user.

---

## 🛡️ Cross-Project Database Backup Process (Disaster Recovery)

Native Cloud SQL automated backups live inside the `pwujczyk-pt` project and are removed if the Cloud SQL instance or project is deleted. To survive full project deletion or `terraform destroy`, both `ptfitness-workouts` and `ptfitness-catalog` are exported daily to a **Cloud Storage bucket in a separate backup GCP project**.

1. **Destination Bucket (Backup Project)**:
   * Create a regional bucket in `europe-central2` (e.g. `gs://pwujczyk-fitness-backups`) with **Object versioning** enabled.
   * Grant **`Storage Object Admin`** (`roles/storage.objectAdmin`) on the bucket to the **Cloud SQL instance's Google-managed service account**:
     ```text
     p213056333340-p4axlu@gcp-sa-cloud-sql.iam.gserviceaccount.com
     ```
     *(Cloud Scheduler only triggers the export API; the Cloud SQL instance itself performs the upload to Cloud Storage).*
   * Add an **IAM Condition** (`Title: Backup from Cloud SQL ptfitness (project pwujczyk-pt)`) so anyone inspecting the backup project knows which project (`213056333340` = `pwujczyk-pt`) owns the service account. Once `ptfitness` is deleted, GCP prefixes the principal with `deleted:serviceAccount:...`, indicating it can be safely removed.
2. **Cloud Scheduler (`pwujczyk-pt`)**:
   * Grant **`Cloud SQL Admin`** (`roles/cloudsql.admin`) to `fitness-vm@pwujczyk-pt.iam.gserviceaccount.com` (or a dedicated `cloud-sql-backup-scheduler` service account).
   * Create two daily HTTP `POST` jobs (at `03:00` and `03:10`) calling `https://sqladmin.googleapis.com/v1/projects/pwujczyk-pt/instances/ptfitness/export` with OAuth token authentication (`https://www.googleapis.com/auth/cloud-platform`) to export `ptfitness-workouts.sql.gz` and `ptfitness-catalog.sql.gz`.