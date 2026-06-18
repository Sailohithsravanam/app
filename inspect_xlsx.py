import os
import fnmatch

def find_file(pattern, path):
    result = []
    for root, dirs, files in os.walk(path):
        for name in files:
            if fnmatch.fnmatch(name, pattern):
                result.append(os.path.join(root, name))
        # Don't go too deep into system dirs to avoid hanging
        if len(result) > 5:
            break
    return result

print("Searching on E:\\...")
try:
    # Avoid scanning whole E:\ if it's too large, check specific directories
    # or just do a quick scan of root folders
    for d in os.listdir("E:\\"):
        if d.lower() in ["$recycle.bin", "system volume information"]:
            continue
        full_d = os.path.join("E:\\", d)
        if os.path.isdir(full_d):
            print(f"Checking E:\\{d}...")
            found = find_file("*E2E_Test_Report_PancreaScan*", full_d)
            if found:
                print("Found:", found)
except Exception as e:
    print("Error scanning E:\\:", e)

print("Searching user directory on C:\\...")
user_dir = os.path.expanduser("~")
print("User dir:", user_dir)
try:
    found = find_file("*E2E_Test_Report_PancreaScan*", user_dir)
    if found:
        print("Found in user dir:", found)
except Exception as e:
    print("Error scanning user dir:", e)
