import os

# --- SIMPLIFIED CONFIGURATION ---
# Save directly to the current folder (Root)
OUTPUT_FILE = "notebookLM/project_context.md"

IGNORED_DIRS = {
    'build', '.gradle', '.git', '.idea', '.vscode', 
    'node_modules', '__pycache__', 'venv', 'target', 
    'dist', 'coverage', 'notebookLM'
}

INCLUDED_EXTENSIONS = {
    '.kt', '.kts', '.xml', '.java', '.gradle',
    '.py', '.js', '.ts', '.jsx', '.tsx', '.html', '.css',
    '.json', '.md', '.yaml', '.yml', '.toml', '.sql'
}
# ---------------------

def generate_context():
    print(f"🚀 Starting Ingest... Scanning: {os.getcwd()}")
    
    # Remove old file
    if os.path.exists(OUTPUT_FILE):
        os.remove(OUTPUT_FILE)

    count = 0
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as outfile:
        outfile.write(f"# Project Codebase Context\n\n")

        for root, dirs, files in os.walk("."):
            # Filter ignored dirs
            dirs[:] = [d for d in dirs if d not in IGNORED_DIRS]
            
            for file in files:
                ext = os.path.splitext(file)[1].lower()
                if ext in INCLUDED_EXTENSIONS:
                    # Skip the scripts themselves
                    if file in ["ingest.py", OUTPUT_FILE]: continue
                    
                    file_path = os.path.join(root, file)
                    rel_path = os.path.relpath(file_path, ".")
                    
                    # Print first 3 files just to prove it's working
                    if count < 3: 
                        print(f"   Reading: {rel_path}")

                    outfile.write(f"\n\n## FILE: {rel_path}\n")
                    outfile.write(f"```\n")
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            outfile.write(f.read())
                            count += 1
                    except Exception as e:
                        print(f"   ⚠️ Error reading {rel_path}: {e}")
                    outfile.write(f"\n```\n")

    print(f"✅ Finished! Scanned {count} files.")
    print(f"📄 Saved to: {os.path.abspath(OUTPUT_FILE)}")

if __name__ == "__main__":
    generate_context()