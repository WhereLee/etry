from http.server import BaseHTTPRequestHandler, HTTPServer
import sys


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        sys.stdout.write(body.decode("utf-8", "replace") + "\n")
        sys.stdout.flush()
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, fmt, *args):
        pass


HTTPServer(("0.0.0.0", 8099), Handler).serve_forever()
