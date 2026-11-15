const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const now = () => new Date();

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user cannot read or write profiles, conversations, messages, or prompts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("prompts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv1").set({
      userId: ALICE_UID,
      title: "Hack",
      modelId: "gemini-3.8-flash",
      lastMessagePreview: "",
      messageCount: 0,
      pinned: false,
      writingMode: "chat",
      createdAt: now(),
      updatedAt: now(),
    })
  );
});

test("User profile: owner can create and update valid profile; cannot inject ghost fields or access another user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const validProfile = {
    userId: ALICE_UID,
    displayName: "Anri Creator",
    email: "alice@example.com",
    bio: "Product designer",
    preferredModelId: "gemini-3.8-flash",
    bengaliVoiceEnabled: true,
    voiceSpeed: 1.0,
    voicePitch: 1.05,
    darkModeOverride: "dark",
    createdAt: now(),
    updatedAt: now(),
  };

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user read & write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: now() })
  );

  // Ghost field injection (Shadow Update) must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: now(),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Anri Pro",
      bengaliVoiceEnabled: false,
      updatedAt: now(),
    })
  );
});

test("Conversations & messages: owner can create, list, update; orphan messages and cross-user reads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");
  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Bengali Poetry",
      modelId: "gemini-3.8-flash",
      lastMessagePreview: "Write a poem",
      messageCount: 1,
      pinned: false,
      writingMode: "bengali",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  // Message under existing conversation succeeds
  const msgRef = convRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "user",
      content: "Write a short Bengali poem about rain.",
      modelId: "gemini-3.8-flash",
      modelName: "Gemini 3.8 Flash",
      attachmentNames: [],
      citationTitles: [],
      citationUrls: [],
      createdAt: now(),
    })
  );

  // Messages are immutable (update must fail)
  await assertFails(msgRef.update({ content: "Tampered" }));

  // Orphan message under non-existent conversation must fail
  const orphanMsgRef = aliceDb
    .collection("users").doc(ALICE_UID)
    .collection("conversations").doc("nonexistent_conv")
    .collection("messages").doc("msg_orphan");
  await assertFails(
    orphanMsgRef.set({
      userId: ALICE_UID,
      conversationId: "nonexistent_conv",
      role: "user",
      content: "Orphan",
      modelId: "gemini-3.8-flash",
      modelName: "Gemini 3.8 Flash",
      attachmentNames: [],
      citationTitles: [],
      citationUrls: [],
      createdAt: now(),
    })
  );

  // Owner can list conversations & messages
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertSucceeds(convRef.collection("messages").get());

  // Cross-user access fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").collection("messages").get()
  );
});

test("Saved prompts: owner CRUD succeeds; cross-user and invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const promptRef = aliceDb.collection("users").doc(ALICE_UID).collection("prompts").doc("prompt_1");
  await assertSucceeds(
    promptRef.set({
      userId: ALICE_UID,
      title: "Code Reviewer",
      body: "Review this Kotlin code for concurrency issues.",
      category: "Coding",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("prompts").get());
  await assertSucceeds(
    promptRef.update({
      title: "Senior Code Reviewer",
      updatedAt: now(),
    })
  );

  // Empty title rejected
  await assertFails(
    promptRef.update({
      title: "",
      updatedAt: now(),
    })
  );

  // Cross-user rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("prompts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("prompts").doc("prompt_1").delete());

  // Owner delete succeeds
  await assertSucceeds(promptRef.delete());
});
