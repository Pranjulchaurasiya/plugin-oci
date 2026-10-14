# Oracle Cloud Infrastructure (OCI) Notifications (ONS) Plugin

The OCI Notifications plugin for Kestra lets workflows create and manage notification topics, configure subscriptions, and publish messages to Oracle Cloud Infrastructure (OCI) Notification Service (ONS).

## Authentication

All OCI tasks inherit credentials and connection properties from `AbstractOciTask`. You can configure authentication details directly on tasks or pass them via flow variables and Kestra secrets:

- `region`: The OCI region identifier (e.g. `eu-frankfurt-1`, `us-ashburn-1`).
- `tenancyOcid`: The OCID of your OCI tenancy (`ocid1.tenancy.oc1..`).
- `userId`: The OCID of the calling user (`ocid1.user.oc1..`).
- `fingerprint`: Fingerprint for the RSA key pair uploaded to your OCI user account.
- `privateKey`: The RSA private key in PEM format. Always stored as a secret in Kestra.
- `passphrase`: Optional passphrase used to decrypt the private key if encrypted.

Example authentication block:

```yaml
tasks:
  - id: sample_task
    type: io.kestra.plugin.oci.ons.PublishMessage
    region: eu-frankfurt-1
    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
    userId: "{{ secret('OCI_USER_OCID') }}"
    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
```

## Available Tasks

The `io.kestra.plugin.oci.ons` sub-plugin provides six tasks to manage topics, configure subscriptions, and publish messages:

1. `PublishMessage`: Publishes messages with optional title and body to an OCI notification topic.
2. `CreateTopic`: Creates a new notification topic in a compartment.
3. `ListTopics`: Lists topics within a compartment with automatic multi-page pagination.
4. `DeleteTopic`: Deletes a topic by OCID.
5. `CreateSubscription`: Subscribes an endpoint (HTTPS, Slack, PagerDuty, email, SMS) to a topic.
6. `DeleteSubscription`: Unsubscribes an endpoint by subscription ID.
