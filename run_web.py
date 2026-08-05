from flask import Flask, send_from_directory
import os

app = Flask(__name__, static_folder=".")

@app.route("/")
def index():
    return send_from_directory(".", "index.html")

@app.route("/<path:path>")
def static_files(path):
    return send_from_directory(".", path)

if __name__ == "__main__":
    print("Serving web app via Flask on http://localhost:3000")
    app.run(host="0.0.0.0", port=3000, debug=False, threaded=True)
