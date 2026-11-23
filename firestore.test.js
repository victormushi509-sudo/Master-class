const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user profiles or projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
});

test("Owner can create and update UserProfile, VideoProject, GeneratedImage, GenerationJob, and AppFeedback", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Victor Mushi",
      email: "victormushi509@gmail.com",
      preferredStyle: "Cinematic",
      preferredLanguage: "English",
      dailyQuotaUsed: 0,
      dailyQuotaLimit: 12,
      quotaDateKey: "2026-11-23",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyQuotaUsed: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set({
        userId: ALICE_UID,
        title: "Ocean Horizon Reel",
        mode: "PROMPT",
        prompt: "Sunrise over ocean cliffs with waves",
        script: "Watch the ocean awaken.",
        aspectRatio: "RATIO_9_16",
        durationSeconds: 15,
        visualStyle: "Cinematic",
        language: "English",
        narrationEnabled: true,
        subtitlesEnabled: true,
        sceneCount: 3,
        status: "DRAFT",
        scenesJson: "[]",
        videoWidth: 720,
        videoHeight: 1280,
        fileSizeBytes: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Rename partial update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .update({
        title: "Ocean Horizon v2",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("generated_images")
      .doc("img_1")
      .set({
        userId: ALICE_UID,
        prompt: "Electric blue cyber mountain",
        style: "Photorealistic",
        aspectRatio: "RATIO_16_9",
        editedFromSource: false,
        localImagePath: "/data/user/0/app/files/media/img_1.jpg",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("generation_jobs")
      .doc("job_1")
      .set({
        userId: ALICE_UID,
        projectId: "proj_1",
        jobType: "VIDEO",
        status: "RUNNING",
        progressPercent: 40,
        statusMessage: "Rendering scenes...",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("app_feedback")
      .doc("fb_1")
      .set({
        userId: ALICE_UID,
        rating: 5,
        opinion: "Clean, fast AI video creator!",
        userEmail: "victormushi509@gmail.com",
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Cross-user isolation and schema poisoning are strictly denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_alice")
      .set({
        userId: ALICE_UID,
        title: "Alice Private Reel",
        mode: "PROMPT",
        prompt: "Mountains",
        script: "Peak sunrise",
        aspectRatio: "RATIO_16_9",
        durationSeconds: 10,
        visualStyle: "Photorealistic",
        language: "English",
        narrationEnabled: false,
        subtitlesEnabled: true,
        sceneCount: 2,
        status: "COMPLETED",
        scenesJson: "[]",
        videoWidth: 1280,
        videoHeight: 720,
        fileSizeBytes: 102400,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Bob cannot read, list, update, or delete Alice's project
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_alice")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_alice")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );

  // Ghost field & @DocumentId 'id' field rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_bad")
      .set({
        id: "proj_bad",
        userId: ALICE_UID,
        title: "Bad Project",
        mode: "PROMPT",
        prompt: "Test",
        script: "Test",
        aspectRatio: "RATIO_1_1",
        durationSeconds: 10,
        visualStyle: "Cinematic",
        language: "English",
        narrationEnabled: false,
        subtitlesEnabled: false,
        sceneCount: 2,
        status: "DRAFT",
        scenesJson: "[]",
        videoWidth: 720,
        videoHeight: 720,
        fileSizeBytes: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("app_feedback")
      .doc("fb_future")
      .set({
        userId: ALICE_UID,
        rating: 5,
        opinion: "Future",
        userEmail: "alice@example.com",
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );
});
