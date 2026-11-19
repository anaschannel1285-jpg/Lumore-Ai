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

test("Unauthenticated user: denied across profiles, chatThreads, and messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chatThreads").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chatThreads").doc("t1").get()
  );
});

test("UserProfile: owner can create and update own profile; cross-user and list denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@gmail.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice M.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(aliceDb.collection("users").get());
});

test("ChatThread & ChatMessage: atomic batch creation, bookmark toggle, rename, immutability, and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 8000);

  const threadRef = aliceDb.collection("users").doc(ALICE_UID).collection("chatThreads").doc("thread_1");
  const msg1Ref = threadRef.collection("messages").doc("msg_1");
  const msg2Ref = threadRef.collection("messages").doc("msg_2");

  // Empty list works
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("chatThreads").get());

  // Atomic batch creating thread + user message + assistant message
  const batch = aliceDb.batch();
  batch.set(threadRef, {
    userId: ALICE_UID,
    title: "Brainstorming sci-fi concept art",
    lastMessagePreview: "Here are three visual directions...",
    bookmarked: false,
    messageCount: 2,
    createdAt: created,
    updatedAt: created,
  });
  batch.set(msg1Ref, {
    userId: ALICE_UID,
    threadId: "thread_1",
    role: "USER",
    text: "Help me brainstorm a cyber-botanical city concept",
    hasImage: true,
    createdAt: created,
  });
  batch.set(msg2Ref, {
    userId: ALICE_UID,
    threadId: "thread_1",
    role: "ASSISTANT",
    text: "Here are three visual directions with bioluminescent canopies and glass walkways.",
    hasImage: false,
    createdAt: new Date(Date.now() - 7000),
  });
  await assertSucceeds(batch.commit());

  // Bookmark toggle succeeds
  await assertSucceeds(
    threadRef.update({
      bookmarked: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Rename succeeds
  await assertSucceeds(
    threadRef.update({
      title: "Cyber-Botanical Concept",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Messages are immutable on update
  await assertFails(msg1Ref.update({ text: "Tampered history" }));

  // Ghost fields / @DocumentId `id` rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatThreads").doc("thread_bad").set({
      id: "thread_bad",
      userId: ALICE_UID,
      title: "Bad",
      lastMessagePreview: "",
      bookmarked: false,
      messageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user thread & message reads fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chatThreads").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chatThreads").doc("thread_1").collection("messages").get());
});
