var dataDir = typeof DATA_DIR === "string" ? DATA_DIR : "deploy/local-data/mongodb/yygh_hosp";

function readText(path) {
  var text = cat(path);
  if (text && text.charCodeAt(0) === 0xFEFF) {
    text = text.substring(1);
  }
  return text;
}

function readJsonArray(fileName) {
  return JSON.parse(readText(dataDir + "/" + fileName));
}

function reviveDates(value) {
  if (value && typeof value === "object") {
    if (value.$date) {
      return new Date(value.$date);
    }
    if (Array.isArray(value)) {
      return value.map(reviveDates);
    }
    Object.keys(value).forEach(function (key) {
      value[key] = reviveDates(value[key]);
    });
  }
  return value;
}

function restoreCollection(name, fileName) {
  var docs = readJsonArray(fileName).map(reviveDates);
  db.getCollection(name).drop();
  if (docs.length > 0) {
    db.getCollection(name).insertMany(docs);
  }
  return docs.length;
}

var restored = {
  Hospital: restoreCollection("Hospital", "Hospital.json"),
  Department: restoreCollection("Department", "Department.json"),
  Schedule: restoreCollection("Schedule", "Schedule.json")
};

db.Hospital.createIndex({ hoscode: 1 }, { unique: true });
db.Hospital.createIndex({ hosname: 1 });
db.Department.createIndex({ hoscode: 1 });
db.Department.createIndex({ depcode: 1 }, { unique: true });
db.Schedule.createIndex({ hoscode: 1 });
db.Schedule.createIndex({ depcode: 1 });
db.Schedule.createIndex({ hosScheduleId: 1 });
db.Schedule.createIndex({ hoscode: 1, depcode: 1, workDate: 1 });

printjson({
  database: db.getName(),
  restored: restored,
  sampleHospital: db.Hospital.findOne({ hoscode: "1000_0" }, { _id: 0, hoscode: 1, hosname: 1, status: 1 }),
  departmentsForSampleHospital: db.Department.count({ hoscode: "1000_0" }),
  schedulesForSampleHospital: db.Schedule.count({ hoscode: "1000_0" })
});