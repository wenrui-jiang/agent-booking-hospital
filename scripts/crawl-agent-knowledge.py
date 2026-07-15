#!/usr/bin/env python3
import argparse
import json
import re
import sys
import urllib.request
from html.parser import HTMLParser
from urllib.parse import urlparse


ALLOWED_DOMAINS = {
    "www.pumch.cn",
    "pumch.cn",
    "www.bjh.com.cn",
    "www.pkuph.cn",
}


class TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.skip = False
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style", "noscript"}:
            self.skip = True

    def handle_endtag(self, tag):
        if tag in {"script", "style", "noscript"}:
            self.skip = False

    def handle_data(self, data):
        if not self.skip:
            text = re.sub(r"\s+", " ", data).strip()
            if text:
                self.parts.append(text)


def fetch_text(url):
    domain = urlparse(url).netloc.lower()
    if domain not in ALLOWED_DOMAINS:
        raise ValueError(f"domain not allowed: {domain}")
    request = urllib.request.Request(url, headers={"User-Agent": "yygh-agent-demo/1.0"})
    with urllib.request.urlopen(request, timeout=15) as response:
        html = response.read().decode("utf-8", errors="ignore")
    parser = TextExtractor()
    parser.feed(html)
    return "\n".join(parser.parts)


def chunk_text(text, max_chars=700):
    sentences = re.split(r"(?<=[。！？；])", text)
    chunks = []
    current = ""
    for sentence in sentences:
        if len(current) + len(sentence) > max_chars and current:
            chunks.append(current.strip())
            current = sentence
        else:
            current += sentence
    if current.strip():
        chunks.append(current.strip())
    return [item for item in chunks if len(item) >= 80]


def main():
    parser = argparse.ArgumentParser(description="Crawl whitelisted hospital pages into Agent knowledge JSONL.")
    parser.add_argument("--url", action="append", required=True, help="Whitelisted source URL. Can be repeated.")
    parser.add_argument("--output", default="deploy/local-data/agent-knowledge/crawled_knowledge.jsonl")
    parser.add_argument("--hospital-code", default="")
    parser.add_argument("--hospital-name", default="")
    parser.add_argument("--department", default="")
    args = parser.parse_args()

    with open(args.output, "w", encoding="utf-8") as out:
        for url in args.url:
            text = fetch_text(url)
            for index, chunk in enumerate(chunk_text(text), start=1):
                out.write(json.dumps({
                    "source_type": "CRAWLED_WEB",
                    "source_url": url,
                    "hospital_code": args.hospital_code,
                    "hospital_name": args.hospital_name,
                    "department": args.department,
                    "title": f"{args.department or args.hospital_name or '医院知识'}-{index}",
                    "content": chunk,
                    "metadata": {"requires_review": True},
                }, ensure_ascii=False) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
