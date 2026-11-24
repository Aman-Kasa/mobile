# BURDEW
## Offline-First Electricity and Water Bill Collection Platform
### Asmara, Eritrea

PROJECT TYPE: Android Mobile Application + Offline Mapping + SMS Communication + Future Backend Infrastructure

WORKING PRODUCT NAME: BURDEW

INITIAL MARKET: Asmara, Eritrea

PRIMARY PLATFORM: Android

INITIAL SERVICES: Electricity and Water Bill Collection

CORE PRINCIPLE: OFFLINE FIRST — THE APPLICATION MUST REMAIN FUNCTIONAL WITHOUT INTERNET CONNECTIVITY.

---

# PART 1: YOUR ROLE

Act as a senior software architect, Android engineer, backend engineer, database engineer, GIS/offline mapping specialist, cybersecurity engineer, and UI/UX designer.

You are responsible for designing and developing BURDEW, a practical mobile application that digitizes and manages an independent electricity and water bill collection service in Eritrea.

Think like an engineer building a real-world production system for an environment with limited internet connectivity, unreliable infrastructure, and constrained resources.

Do not develop this as a generic demonstration application.

Build a reliable, lightweight, maintainable system that solves a genuine operational problem.

This is a NEW, standalone project built from scratch. It is not connected to, and does not reuse code from, any earlier application.

Before writing code, establish the architecture and a development plan. Once code exists, inspect it before each new phase and do not unnecessarily replace working code.

---

# PART 2: PRODUCT IDENTITY

The working product name is BURDEW.

The brand name is provisional and can be changed later without requiring a complete application rewrite.

Separate product branding from technical implementation.

Suggested naming:

- Product: BURDEW
- Android application: BURDEW
- Android repository: burdew-android
- Local database: burdew.db
- Offline map module: burdew-asmara-maps
- GPS module: burdew-location
- SMS module: burdew-sms
- Transaction module: burdew-transactions
- Future backend: burdew-hub
- Backend API: burdew-api
- Central database: burdew_core
- Admin dashboard: burdew-admin
- Documentation: burdew-docs

Suggested Android application package:

com.burdew.app

Confirm the package name is not already in use by another published app before release.

Do not hardcode the product name throughout business logic.

Use centralized application configuration and Android string resources so branding can be changed later.

Suggested tagline:

"Your Utility Bills, Made Easier."

BURDEW is an independent service platform. It is not an official government application or an official electricity or water utility application.

Never imply government endorsement or direct integration with utility institutions unless such authorization and integration actually exist.

---

# PART 3: THE PROBLEM

BURDEW addresses difficulties associated with electricity and water bill payment in Eritrea, particularly Asmara.

The initial problem includes:

1. Limited internet connectivity.
2. Limited availability of mobile internet services.
3. Customers needing to physically travel to payment offices.
4. Long waiting times and inconvenient payment processes.
5. Transportation costs and time spent travelling.
6. Difficulty accessing payment services for busy people, elderly residents, and customers living far from payment offices.

The application introduces a local bill collection service.

Instead of personally visiting a payment office, a customer can request assistance from a local service provider.

The provider visits the customer, receives the required payment or information, visits the appropriate payment institution, completes the payment, and delivers the official receipt.

BURDEW digitizes and organizes the process.

---

# PART 4: BUSINESS MODEL

BURDEW connects three main participants:

1. Customers.
2. Local service providers.
3. Electricity and water payment institutions.

## Customer workflow

1. Customer requests electricity or water bill collection.
2. Customer provides identifying information.
3. Service provider receives the request.
4. Service provider visits the customer.
5. Provider collects the agreed payment or required information.
6. Provider visits the appropriate payment office.
7. Provider completes the payment.
8. Provider obtains the official payment receipt.
9. Provider delivers the receipt to the customer.
10. The application records the completed transaction.

## Service provider compensation

The current business model provides:

50 NFA per successfully completed transaction.

The system must calculate service provider earnings automatically.

For example:

- 1 completed transaction = 50 NFA.
- 10 completed transactions = 500 NFA.
- 100 completed transactions = 5,000 NFA.

Only verified completed transactions should generate earnings.

Cancelled, rejected, duplicated, or incomplete transactions must not generate compensation.

Make the compensation amount configurable because the business may change its pricing model later.

## Important financial distinction

Track separately:

- Customer utility bill amount.
- Amount collected from customer.
- Amount paid to the utility institution.
- Service provider compensation.
- Any additional service charges.
- Refunds or outstanding balances.

Never treat the utility bill amount as BURDEW revenue.

---

# PART 5: TARGET GEOGRAPHY

The initial deployment location is Asmara, Eritrea.

The application must support offline geographical operations.

Future expansion may include other cities and regions in Eritrea.

The map architecture should therefore support multiple downloadable geographical areas rather than being permanently restricted to Asmara.

Initial geographical data:

- Asmara streets.
- Residential areas.
- Main roads.
- Important landmarks.
- Relevant public facilities.
- Customer locations.

Use appropriate, legally reusable geographical data.

OpenStreetMap is a possible source for geographical information, subject to its licensing and attribution requirements.

Do not assume that an online map will always be available.

---

# PART 6: ANDROID TECHNOLOGY STACK

Develop a native Android application.

Preferred technology:

- Java (chosen for this project; Kotlin can be introduced module by module later).
- Android Studio.
- Android SDK.
- Room Database.
- SQLite.
- Android LocationManager.
- GPS_PROVIDER.
- Offline-compatible mapping technology.
- Native SMS APIs, subject to Android permissions and platform restrictions.
- Android WorkManager where appropriate for deferred background tasks.
- Material Design principles for the interface.

Recommended architecture:

- MVVM.
- Repository pattern.
- Data Access Objects.
- Service layer.
- Dependency injection where justified.
- Modular feature organization.

Keep the application lightweight.

Do not introduce unnecessary dependencies.

Avoid making Google Play Services mandatory for essential functionality.

Do not mix in a Kotlin rewrite without a clear technical reason.

---

# PART 7: OFFLINE-FIRST ARCHITECTURE

This is the most important requirement.

BURDEW must function without internet access.

Do not build an application that merely displays an offline screen when disconnected.

Essential features must continue working.

The application must support:

- Customer registration.
- Customer information retrieval.
- Service request creation.
- Request updates.
- GPS coordinate collection.
- Offline map display.
- Transaction management.
- Receipt capture.
- Local reports.
- Service provider management.
- Local data export.
- SMS-based communication where supported.

All essential information must be stored locally.

Use Room Database as the main persistence layer.

The application should be designed so that future server synchronization can be added without changing the fundamental business logic.

Do not make cloud services necessary for normal application operation.

---

# PART 8: OFFLINE MAP SYSTEM — ASMARA

Develop an offline map system for Asmara.

This is a core feature, not an optional enhancement.

The map must display geographical information without requiring an internet connection.

Recommended mapping approaches:

- MapLibre Native with appropriately prepared offline resources.
- Mapsforge.
- Another suitable OpenStreetMap-based offline mapping implementation.

Choose the most appropriate solution after evaluating storage, performance, licensing, and Android compatibility.

## Map requirements

The map must support:

1. Offline rendering.
2. Zooming and panning.
3. Displaying roads and streets.
4. Displaying important landmarks.
5. Showing current GPS location.
6. Displaying customer location markers.
7. Saving customer coordinates.
8. Manually selecting locations.
9. Viewing saved customer locations.
10. Reopening the map without internet.
11. Searching locally indexed places where supported.
12. Managing offline geographical data.

## Map data

Prepare an offline map package covering Asmara.

Store the map resources locally.

Use a suitable vector or raster tile format depending on the chosen mapping technology.

Include proper geographical data attribution.

Do not assume that an ordinary Google Maps installation automatically provides the offline functionality required by BURDEW.

## Map interface

Provide:

- Current location button.
- Zoom controls where appropriate.
- Customer markers.
- Location selection.
- Search functionality.
- Map layer information.
- Offline map availability indicator.

When offline geographical data is missing, show a clear message instead of silently attempting to load an online map.

---

# PART 9: GPS AND LOCATION MANAGEMENT

Use Android's native LocationManager.

Prioritize GPS_PROVIDER for precise outdoor positioning.

The application should:

- Request location permissions appropriately.
- Obtain current GPS coordinates.
- Capture customer locations.
- Store latitude and longitude.
- Associate locations with customer records.
- Associate locations with service requests.
- Display customer markers on the offline map.
- Support manual location selection.
- Handle unavailable GPS signals.
- Handle disabled GPS.
- Handle denied permissions.

Do not continuously track users unnecessarily.

Use location collection when creating a request, updating an address, or performing another meaningful operation.

Provide manual address entry as a fallback.

Include timestamps and location accuracy information when available.

---

# PART 10: SMS COMMUNICATION

SMS is essential because mobile internet cannot be assumed.

Design an SMS communication system that can operate using supported mobile networks.

## Customer SMS requests

A customer should be able to request electricity or water bill collection using a structured SMS.

Example:

SERVICE: ELECTRICITY
NAME: Customer Name
PHONE: Customer Phone
ADDRESS: Customer Address
ACCOUNT: Account Reference

The format should be simple and easy to understand.

Support both structured messages and manual request entry.

## SMS processing

Where device capabilities and permissions permit:

- Receive incoming request messages.
- Identify sender phone numbers.
- Parse supported message formats.
- Validate request contents.
- Create service requests.
- Detect duplicate messages.
- Save received messages.
- Record processing status.
- Send acknowledgement messages.
- Allow authorized operators to respond.

## SMS reliability

Account for:

- Duplicate SMS messages.
- Invalid message formats.
- Incomplete customer details.
- Delivery failures.
- Network availability.
- SMS length limitations.
- Character encoding.
- Device permission restrictions.

Do not assume SMS delivery guarantees.

Record outgoing message status where the Android platform allows it.

Provide manual workflows when automatic SMS reception or transmission is unavailable.

Do not silently delete unprocessed messages.

---

# PART 11: APPLICATION USERS AND ROLES

Support different user roles.

## 1. Administrator

Responsible for managing the service.

Can:

- View all customers.
- Manage service providers.
- View all service requests.
- Assign requests.
- View transaction histories.
- View financial reports.
- Configure service fees.
- Manage application settings.
- Export and back up data.

## 2. Service provider

Responsible for completing collection tasks.

Can:

- View assigned requests.
- View customer details.
- Access offline maps.
- Navigate to customer locations.
- Update request status.
- Record payment information.
- Photograph receipts.
- View completed transactions.
- View personal earnings.

## 3. Customer

The initial service may operate primarily through SMS and direct communication rather than requiring every customer to install the application.

Design the architecture so that a dedicated customer application or customer portal can be introduced later.

Do not create unnecessary complexity by forcing all customers to use the Android application.

---

# PART 12: USER INTERFACE

Design a modern, lightweight, professional mobile interface.

The interface must be easy to understand.

Avoid excessive animations, unnecessary graphics, and complicated navigation.

Use a consistent design system.

Suggested visual identity:

- Primary color: Deep blue.
- Secondary color: Teal.
- Accent: Warm yellow.
- Background: Light neutral.
- Typography: Clear and readable.

Support future branding changes through centralized themes and resources.

## SCREEN 1: SPLASH SCREEN

Display:

- BURDEW logo.
- Product name.
- Tagline.
- Application initialization.

## SCREEN 2: ONBOARDING

Explain:

- What BURDEW does.
- How bill collection works.
- How GPS and offline maps are used.
- How customer information is protected.

## SCREEN 3: LOGIN / ACCESS

Provide appropriate operator and service provider access.

For a fully offline initial deployment, design local access controls.

Do not depend on cloud authentication.

Include secure local credential handling and appropriate role permissions.

## SCREEN 4: DASHBOARD

Display:

- Total customers.
- New requests.
- Pending requests.
- Assigned requests.
- Completed transactions.
- Total service provider earnings.
- Recent activity.

Provide quick actions:

- New request.
- Customers.
- Service providers.
- Offline map.
- Transactions.
- Reports.

## SCREEN 5: CUSTOMER REGISTRATION

Fields:

- Full name.
- Phone number.
- Alternative phone number.
- Address.
- Service type.
- Account reference.
- Latitude.
- Longitude.
- Additional information.

Allow location selection through the offline map.

## SCREEN 6: SERVICE REQUEST CREATION

Fields:

- Customer.
- Electricity or water.
- Account reference.
- Customer address.
- GPS coordinates.
- Request date.
- Additional instructions.
- Priority.
- Assigned service provider.

## SCREEN 7: ASMARA OFFLINE MAP

Display:

- Offline map.
- Current position.
- Customer locations.
- Saved landmarks.
- Search.
- Manual location selection.
- Navigation-related information.

## SCREEN 8: CUSTOMER DIRECTORY

Allow users to:

- Search customers.
- View customer details.
- Edit information.
- View previous requests.
- View transaction histories.
- View saved locations.

## SCREEN 9: SERVICE PROVIDER MANAGEMENT

Allow administrators to:

- Register providers.
- Edit provider details.
- Activate or deactivate accounts.
- Assign requests.
- View completed tasks.
- View earnings.
- View transaction histories.

## SCREEN 10: REQUEST MANAGEMENT

Use clear statuses:

- NEW.
- PENDING.
- ASSIGNED.
- IN_PROGRESS.
- PAYMENT_COMPLETED.
- RECEIPT_DELIVERED.
- CANCELLED.
- FAILED.

Allow authorized status transitions.

Record who made each change and when.

## SCREEN 11: TRANSACTION MANAGEMENT

Record:

- Transaction ID.
- Request ID.
- Customer.
- Provider.
- Service type.
- Bill amount.
- Amount collected.
- Amount paid.
- Service fee.
- Transaction date.
- Payment status.
- Receipt reference.
- Additional notes.

## SCREEN 12: RECEIPT MANAGEMENT

Allow providers to:

- Photograph receipts.
- Save receipt images.
- Associate photographs with transactions.
- View previous receipts.
- Search receipts.
- Export receipt information.

Store image files locally using durable application-managed storage.

Do not store large photographs directly in SQLite database columns.

## SCREEN 13: SMS MANAGEMENT

Display:

- Received messages.
- Outgoing messages.
- Processed messages.
- Failed messages.
- Unprocessed requests.

Allow manual processing where automatic parsing fails.

## SCREEN 14: REPORTS

Provide:

- Daily transaction reports.
- Weekly reports.
- Monthly reports.
- Electricity collections.
- Water collections.
- Completed transactions.
- Cancelled transactions.
- Provider earnings.
- Outstanding requests.
- Service fee summaries.

## SCREEN 15: SETTINGS

Include:

- Language.
- Theme.
- SMS settings.
- Location settings.
- Offline map management.
- Data backup.
- Data export.
- Access control.
- Application information.

---

# PART 13: DATABASE DESIGN

Use Room Database.

Create a normalized database with appropriate entities and relationships.

Suggested entities:

## Customers

- customerId
- fullName
- phoneNumber
- alternativePhone
- address
- latitude
- longitude
- createdAt
- updatedAt

## ServiceProviders

- providerId
- fullName
- phoneNumber
- username
- passwordHash
- role
- activeStatus
- createdAt

## ServiceRequests

- requestId
- customerId
- serviceType
- accountReference
- description
- latitude
- longitude
- assignedProviderId
- requestStatus
- createdAt
- updatedAt

## Transactions

- transactionId
- requestId
- customerId
- providerId
- serviceType
- billAmount
- amountCollected
- amountPaid
- serviceFee
- paymentStatus
- transactionDate

## Receipts

- receiptId
- transactionId
- imagePath
- receiptReference
- capturedAt
- notes

## SMSMessages

- messageId
- phoneNumber
- messageBody
- messageType
- processingStatus
- receivedAt
- relatedRequestId

## LocationRecords

- locationId
- customerId
- latitude
- longitude
- accuracy
- capturedAt
- locationSource

## AuditLogs

- logId
- userId
- entityType
- entityId
- action
- previousValue
- newValue
- timestamp

## SynchronizationQueue

- syncId
- entityType
- entityId
- operationType
- syncStatus
- createdAt
- lastAttemptAt

Use UUIDs or another globally unique identifier strategy for records that may eventually synchronize across devices.

Add appropriate indexes, foreign keys, database migrations, and validation rules.

Prevent duplicate transactions.

Maintain referential integrity.

Use database transactions when multiple related records must be updated together.

---

# PART 14: SECURITY

Protect customer information and transaction records.

Implement:

- Local access controls.
- Secure credential storage.
- Password hashing using an appropriate modern algorithm.
- Input validation.
- Safe file storage.
- Database integrity checks.
- Audit logs.
- Backup protection.
- Role-based authorization.
- Protection against accidental data deletion.

Do not hardcode passwords, API keys, or secrets.

Do not expose customer information unnecessarily.

Do not mark a payment complete without appropriate payment evidence.

Differentiate between:

1. Request created.
2. Payment collected from customer.
3. Payment submitted to utility institution.
4. Payment verified.
5. Receipt delivered.

Only mark a transaction fully completed when the required verification conditions are satisfied.

---

# PART 15: FUTURE BACKEND — BURDEW HUB

The initial application should function without a central server.

However, the system must be architected for future multi-device synchronization.

The future central backend will be called:

BURDEW HUB.

Suggested backend technology:

- Java with Spring Boot, or another suitable backend framework.
- PostgreSQL.
- REST API.
- Secure authentication.
- Role-based authorization.
- Synchronization service.

Suggested API responsibilities:

- Customer synchronization.
- Service provider synchronization.
- Service request synchronization.
- Transaction synchronization.
- Receipt metadata synchronization.
- Reporting.
- Administrative management.

## Synchronization architecture

Every device should maintain its own local database.

When connectivity becomes available, the application can synchronize eligible changes with BURDEW HUB.

Synchronization must support:

- Offline-created records.
- Unique transaction identifiers.
- Duplicate detection.
- Retry mechanisms.
- Conflict handling.
- Timestamp management.
- Secure data transmission.
- Synchronization status.

Do not assume that the server is always reachable.

Do not lose locally recorded transactions when synchronization fails.

Design synchronization carefully so that the same payment is not counted twice.

If the business later requires a single authoritative transaction ledger, establish appropriate server-side validation and conflict resolution rules.

## Future infrastructure

Possible infrastructure:

- VPS hosting.
- PostgreSQL database.
- Backend API.
- Admin dashboard.
- Encrypted backups.
- Monitoring.

Do not deploy a server unnecessarily during the first development stage.

---

# PART 16: ADMIN DASHBOARD

Design a future web-based administration interface.

The dashboard should allow administrators to:

- Manage customers.
- Manage service providers.
- Assign service requests.
- Monitor transactions.
- View provider performance.
- View financial summaries.
- Review receipts.
- Manage system settings.
- Review synchronization status.
- Export reports.

The dashboard must communicate with BURDEW HUB through authenticated APIs.

It should not directly access mobile device databases.

---

# PART 17: BACKUP AND DATA TRANSFER

The system must support data backup without requiring internet.

Implement appropriate local export and import capabilities.

Possible methods:

- USB file transfer.
- Bluetooth.
- Nearby Share or compatible local sharing.
- Local network transfer.
- Manual backup files.

Allow authorized users to export:

- Customer records.
- Service requests.
- Transaction records.
- Provider records.
- Receipt metadata.
- Reports.

Protect sensitive information during export.

Validate imported files.

Prevent accidental replacement of newer records.

Keep a backup before destructive imports.

---

# PART 18: LANGUAGE AND LOCALIZATION

Initially support English.

Prepare the application for:

- Tigrinya.
- Arabic.

Use Android string resources.

Avoid hardcoding text in Java or Kotlin files.

Support right-to-left layout for Arabic.

Use appropriate fonts and translations.

Have native speakers validate translations before production release.

---

# PART 19: PERFORMANCE AND DEVICE COMPATIBILITY

The application must work on affordable Android smartphones.

Optimize:

- Application size.
- Memory consumption.
- Battery consumption.
- Database queries.
- Offline map rendering.
- Receipt image compression.
- Background processing.

Avoid unnecessary continuous GPS tracking.

Avoid unnecessary background services.

Ensure that the application can run without a Google account or Google Play Services for its core offline features.

Consider older Android versions where technically feasible, while respecting modern Android security requirements.

---

# PART 20: TESTING

Test the application under realistic conditions.

## Offline tests

- Disable mobile data.
- Disable Wi-Fi.
- Restart the application.
- Create customer records.
- Create service requests.
- Open the offline map.
- Capture GPS coordinates.
- Save transactions.
- Photograph receipts.
- Generate reports.

All essential local functionality should continue working.

## SMS tests

- Send valid requests.
- Send malformed requests.
- Send duplicate messages.
- Test acknowledgement messages.
- Test failed delivery.
- Test permission denial.

## Database tests

- Insert records.
- Update records.
- Delete permitted records.
- Test relationships.
- Test duplicate detection.
- Test database migration.
- Test backup and restoration.

## GPS tests

- GPS enabled.
- GPS disabled.
- Permission denied.
- Poor GPS signal.
- Manual location selection.

## Security tests

- Unauthorized access.
- Invalid credentials.
- Unauthorized transaction modification.
- Accidental deletion.
- Backup integrity.

## Synchronization tests

When BURDEW HUB is introduced:

- Multiple phones creating requests.
- Duplicate synchronization attempts.
- Network interruption.
- Conflicting record updates.
- Failed synchronization retries.
- Recovery after server downtime.

---

# PART 21: DEVELOPMENT ROADMAP

Develop the project incrementally.

Do not attempt to implement every feature simultaneously.

## PHASE 1: FOUNDATION

- Create the new project.
- Establish architecture.
- Configure project identity.
- Build UI foundation.
- Configure Room Database.
- Implement customer management.

## PHASE 2: SERVICE MANAGEMENT

- Request creation.
- Request tracking.
- Provider management.
- Request assignment.
- Transaction records.

## PHASE 3: GPS AND OFFLINE MAPS

- Native GPS.
- Asmara geographical data.
- Offline map rendering.
- Customer markers.
- Manual location selection.

## PHASE 4: SMS COMMUNICATION

- SMS permissions.
- Incoming SMS processing.
- Request parsing.
- Acknowledgements.
- SMS history.
- Error handling.

## PHASE 5: FINANCIAL MANAGEMENT

- Transaction verification.
- Service provider compensation.
- Receipt photographs.
- Transaction reports.
- Earnings summaries.

## PHASE 6: DATA MANAGEMENT

- Backup.
- Import.
- Export.
- Offline data transfer.
- Database recovery.

## PHASE 7: CENTRAL BACKEND

- BURDEW HUB.
- PostgreSQL.
- REST API.
- Authentication.
- Synchronization.
- Multi-device management.

## PHASE 8: EXPANSION

- Admin dashboard.
- Tigrinya.
- Arabic.
- iOS.
- Additional geographical areas.
- Additional household services.

---

# PART 22: DEVELOPMENT RULES FOR THE AI CODING ASSISTANT

Follow these rules throughout development.

1. Inspect the current BURDEW code before modifying it.
2. Understand what has already been built before proposing architectural changes.
3. Preserve working features.
4. Work incrementally.
5. Explain significant technical decisions.
6. Do not fabricate completed features.
7. Do not use placeholder data in production functionality.
8. Do not assume internet connectivity.
9. Do not make cloud services mandatory.
10. Do not introduce unnecessary dependencies.
11. Write maintainable code.
12. Use appropriate error handling.
13. Validate user input.
14. Implement database migrations.
15. Test each feature.
16. Document configuration requirements.
17. Explain Android permissions.
18. Protect customer and transaction data.
19. Avoid unnecessary rewrites.
20. Keep the architecture extensible.

Whenever you implement a feature:

- Explain what you are changing.
- Identify the relevant files.
- Implement the feature.
- Build the application.
- Run relevant tests.
- Report what works.
- Report what remains incomplete.

Never claim a feature works unless it has actually been implemented and tested.

If an external service, permission, map dataset, or hardware capability is unavailable, clearly explain the limitation and provide a practical alternative.

---

# PART 23: FINAL PROJECT OBJECTIVE

Build BURDEW into a reliable offline-first utility bill collection platform for Asmara, Eritrea.

The platform should combine:

- Native Android engineering.
- Offline databases.
- GPS.
- Offline OpenStreetMap-based geographical information.
- SMS communication.
- Customer management.
- Service provider management.
- Financial transaction tracking.
- Receipt management.
- Future multi-device synchronization.

The application should solve an actual local service-delivery problem.

It must remain functional even when internet connectivity is unavailable.

Its architecture must be scalable enough to support additional cities, service providers, services, and future backend infrastructure.

The ultimate goal is to create a practical digital service platform designed around Eritrean infrastructure realities rather than assuming the availability of infrastructure that does not exist.

BUILD FOR THE REAL WORLD.

BUILD FOR ERITREA.

BUILD BURDEW.