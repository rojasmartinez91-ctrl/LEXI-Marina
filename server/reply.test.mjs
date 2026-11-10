import assert from "node:assert/strict";
import test from "node:test";
import { parseReply } from "./reply.mjs";

test("accepts a local asset and emotion", () => {
  const reply = { answer: "Mercurio está cerca del Sol.", emotion: "surprised", resource: "solar" };
  assert.deepEqual(parseReply(JSON.stringify(reply)), reply);
});
test("ignores unknown resources and model-suggested actions", () => {
  assert.deepEqual(parseReply(JSON.stringify({
    answer: "Hola", resource: "https://example.invalid/a.png", emotion: "invalid", action: "youtube"
  })), { answer: "Hola", emotion: "happy", resource: "" });
});
test("rejects invalid or empty replies", () => {
  for (const input of ["invalid", "null", '{"answer":" "}', undefined])
    assert.throws(() => parseReply(input));
});
