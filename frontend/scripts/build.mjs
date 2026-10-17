import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
const cwd = fileURLToPath(new URL("..", import.meta.url));
const marker = new URL("../node_modules/.flowstaff-install", import.meta.url);
const hash = createHash("sha256")
  .update(readFileSync(new URL("../package-lock.json", import.meta.url)))
  .update(process.versions.node.split(".")[0])
  .digest("hex");
const installed = existsSync(marker) && readFileSync(marker, "utf8") === hash;
for (const args of [
  ...(installed ? [] : [["ci", "--no-audit", "--no-fund"]]),
  ["run", "build"],
]) {
  const result = spawnSync("npm", args, {
    cwd,
    stdio: "inherit",
    shell: process.platform === "win32",
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
  if (args[0] === "ci") writeFileSync(marker, hash);
}
