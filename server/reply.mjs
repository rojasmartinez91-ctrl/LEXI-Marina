const emotions = new Set(["happy", "surprised", "confused"]);
const resources = new Set(["solar", "multiply_8_4"]);

export function parseReply(content) {
  if (typeof content !== "string" || content.length > 3000) throw new Error("Invalid reply");
  const value = JSON.parse(content.trim());
  const answer = typeof value?.answer === "string" ? value.answer.trim() : "";
  if (!answer || answer.length > 1500) throw new Error("Invalid answer");
  return {
    answer,
    emotion: emotions.has(value.emotion) ? value.emotion : "happy",
    resource: resources.has(value.resource) ? value.resource : ""
  };
}
