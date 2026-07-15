#!/usr/bin/env python3
import argparse
import json
import os
import sys

import psycopg


def main():
    parser = argparse.ArgumentParser(description="Import reviewed Agent knowledge JSONL into pgvector tables.")
    parser.add_argument("--dsn", default=os.getenv("PGVECTOR_DSN", ""))
    parser.add_argument("--file", action="append", required=True)
    args = parser.parse_args()
    if not args.dsn:
        print("PGVECTOR_DSN is required", file=sys.stderr)
        return 1

    with psycopg.connect(args.dsn) as conn:
        with conn.cursor() as cur:
            for path in args.file:
                with open(path, "r", encoding="utf-8") as handle:
                    for line in handle:
                        item = json.loads(line)
                        cur.execute(
                            """
                            insert into medical_knowledge_chunk(
                              source_type, source_url, hospital_code, hospital_name,
                              department, title, content, metadata_json
                            ) values (%s, %s, %s, %s, %s, %s, %s, %s::jsonb)
                            """,
                            (
                                item.get("source_type") or item.get("memory_type") or "DEMO_SEED",
                                item.get("source_url"),
                                item.get("hospital_code"),
                                item.get("hospital_name"),
                                item.get("department"),
                                item.get("title") or "未命名知识",
                                item.get("content"),
                                json.dumps(item.get("metadata") or {}, ensure_ascii=False),
                            ),
                        )
        conn.commit()
    return 0


if __name__ == "__main__":
    sys.exit(main())
