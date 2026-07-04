# yygh_hosp MongoDB Seed

This directory contains the verified MongoDB seed data for the hospital demo collections used by `service-hosp`.

Verified on 2026-07-04 from local database `yygh_hosp`:

- `Hospital.json`: 1 document, including `北京协和医院` (`hoscode=1000_0`)
- `Department.json`: 288 documents
- `Schedule.json`: 239 documents

## Restore

From the repository root, with MongoDB running on `127.0.0.1:27017`:

```powershell
& "D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30\bin\mongo.exe" yygh_hosp deploy/local-data/mongodb/yygh_hosp/import-yygh-hosp-seed.js
```

On Linux/macOS with `mongo` in `PATH`:

```bash
mongo yygh_hosp deploy/local-data/mongodb/yygh_hosp/import-yygh-hosp-seed.js
```

If running the import script from another working directory, set `DATA_DIR` explicitly:

```bash
mongo yygh_hosp --eval "var DATA_DIR='/path/to/repo/deploy/local-data/mongodb/yygh_hosp'; load(DATA_DIR + '/import-yygh-hosp-seed.js')"
```

The import script drops and recreates only these collections: `Hospital`, `Department`, and `Schedule`.
