import express from "express";
import Anthropic from "@anthropic-ai/sdk";
import dotenv from "dotenv";

dotenv.config();

const app = express();
app.use(express.json());

const anthropic = new Anthropic({
  apiKey: process.env.ANTHROPIC_API_KEY, // set this in your environment, never hardcode
});

// In-memory log for the demo digest endpoint — swap for a real DB in production
const recentNotifications = [];

app.post("/process-notification", async (req, res) => {
  const { app: appPackage, title, text, timestamp } = req.body;

  try {
    const response = await anthropic.messages.create({
      model: "claude-sonnet-4-6",
      max_tokens: 300,
      system:
        "You are TOXX, an AI phone assistant. Given a notification's app, " +
        "title, and text, return ONLY a JSON object with keys: " +
        '"summary" (one short sentence), "suggestedReply" (a natural, brief ' +
        'reply if this looks like a message needing one, else empty string), ' +
        'and "priority" (integer 0-10, how urgent/important this is). ' +
        "No markdown, no extra text — valid JSON only.",
      messages: [
        {
          role: "user",
          content: `App: ${appPackage}\nTitle: ${title}\nText: ${text}`,
        },
      ],
    });

    const raw = response.content
      .filter((block) => block.type === "text")
      .map((block) => block.text)
      .join("");

    const cleaned = raw.replace(/```json|```/g, "").trim();
    const parsed = JSON.parse(cleaned);

    recentNotifications.push({ appPackage, title, text, timestamp, ...parsed });
    res.json(parsed);
  } catch (err) {
    console.error("Claude API error:", err);
    res.status(500).json({ summary: "", suggestedReply: "", priority: 0 });
  }
});

app.post("/voice-command", async (req, res) => {
  const { command } = req.body;

  try {
    const response = await anthropic.messages.create({
      model: "claude-sonnet-4-6",
      max_tokens: 300,
      system:
        "You are TOXX, a voice-controlled AI phone assistant. Given a " +
        "spoken command transcript, return ONLY a JSON object with keys: " +
        '"action" (one of: "call", "digest", "read_notifications", "none"), ' +
        '"target" (e.g. a contact name or number if action is "call", else ' +
        'empty string), and "spokenResponse" (a short, natural sentence ' +
        "TOXX should say back, e.g. \"Calling Mom now.\"). No markdown, " +
        "no extra text — valid JSON only.",
      messages: [{ role: "user", content: command }],
    });

    const raw = response.content
      .filter((block) => block.type === "text")
      .map((block) => block.text)
      .join("");

    const cleaned = raw.replace(/```json|```/g, "").trim();
    const parsed = JSON.parse(cleaned);

    res.json(parsed);
  } catch (err) {
    console.error("Claude API error:", err);
    res.status(500).json({ action: "none", target: "", spokenResponse: "Something went wrong." });
  }
});

app.get("/digest", async (req, res) => {
  const since = Date.now() - 12 * 60 * 60 * 1000; // last 12 hours
  const recent = recentNotifications.filter((n) => n.timestamp >= since);

  if (recent.length === 0) {
    return res.json({ digest: "Nothing notable in the last 12 hours." });
  }

  try {
    const response = await anthropic.messages.create({
      model: "claude-sonnet-4-6",
      max_tokens: 400,
      messages: [
        {
          role: "user",
          content:
            "Summarize these notifications into a short, useful morning digest " +
            "for the phone's owner, grouped by priority:\n\n" +
            JSON.stringify(recent, null, 2),
        },
      ],
    });

    const digest = response.content
      .filter((b) => b.type === "text")
      .map((b) => b.text)
      .join("");

    res.json({ digest });
  } catch (err) {
    console.error("Claude API error:", err);
    res.status(500).json({ digest: "" });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`TOXX backend running on port ${PORT}`));
