import { readFile } from "node:fs/promises";
import vm from "node:vm";

const html = await readFile(new URL("./test-console.html", import.meta.url), "utf8");
const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)];
if (!scripts.length) throw new Error("Jarvis Lab has no inline JavaScript to validate");
for (const [index, match] of scripts.entries()) {
  new vm.Script(match[1], { filename: "test-console-inline-" + index + ".js" });
}
console.log("Jarvis Lab inline JavaScript syntax OK (" + scripts.length + " script block(s))");
