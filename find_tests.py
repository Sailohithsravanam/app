import os

test_files = []
for root, dirs, files in os.walk("e:\\app"):
    if "node_modules" in root or ".gradle" in root or ".git" in root or "build" in root:
        continue
    for f in files:
        if "test" in f.lower():
            test_files.append(os.path.join(root, f))

print("Found test files:")
for tf in test_files:
    print(tf)
