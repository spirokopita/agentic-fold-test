"""Select an observed idle app tab and verify its actual resulting UI in CI."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

package = "com.agentfold.app.recovery"
panel = sys.argv[1]
assert panel in ("Activity", "Task", "Files", "Control", "History")


def adb(*args):
    result = subprocess.run(["adb", *args], capture_output=True, timeout=20, check=True)
    return result.stdout


def read_ui():
    adb("shell", "uiautomator", "dump", "/sdcard/agentfold-workspace.xml")
    raw = adb("exec-out", "cat", "/sdcard/agentfold-workspace.xml")
    root = ET.fromstring(raw)
    assert any(node.get("package") == package for node in root.iter("node"))
    return root, raw


def bounds(node):
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    assert match, "UI node must have observed bounds"
    return tuple(map(int, match.groups()))


root, _ = read_ui()
target = next(node for node in root.iter("node")
              if node.get("content-desc", "").startswith(f"{panel} workspace tab"))
assert target.get("enabled") == "true" and target.get("clickable") == "true"
x1, y1, x2, y2 = bounds(target)
assert x2 > x1 and y2 > y1
adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
deadline = time.monotonic() + 15
while True:
    root, raw = read_ui()
    if any(node.get("content-desc") == f"{panel} workspace tab, selected"
           for node in root.iter("node")):
        break
    if time.monotonic() >= deadline:
        raise AssertionError("Observed tap did not select the requested workspace")
    time.sleep(.1)

if panel == "Task":
    button = next(node for node in root.iter("node") if node.get("text") == "Run approved agent task")
    x1, _, x2, _ = bounds(button)
    physical = adb("shell", "wm", "size").decode()
    width = int(re.findall(r"(?:Physical|Override) size: (\d+)x\d+", physical)[-1])
    assert x2 - x1 >= width * .85, "Wide focused Task panel must use available width"
    print(f"Verified Task action width: {x2 - x1}/{width} pixels")
Path(f"install-evidence/workspace-wide-{panel.lower()}.xml").write_bytes(raw)
print(f"Verified observed {panel} selection; no task or device-control action was invoked")
