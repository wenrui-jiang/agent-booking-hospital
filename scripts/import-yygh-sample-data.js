var root = typeof DATA_ROOT === "string" ? DATA_ROOT : ".";
var dataDir = typeof DATA_DIR === "string" ? DATA_DIR : root + "/资料/05-医院接口模拟系统/示例数据";
var hospitalPath = dataDir + "/hospital.json";
var departmentPath = dataDir + "/department.json";
var schedulePath = dataDir + "/schedule.json";

function parseJsonFile(path) {
  return JSON.parse(cat(path));
}

function midnight(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function yyyyMMdd(date) {
  var m = String(date.getMonth() + 1);
  var d = String(date.getDate());
  if (m.length < 2) m = "0" + m;
  if (d.length < 2) d = "0" + d;
  return date.getFullYear() + "-" + m + "-" + d;
}

var now = new Date();
var today = midnight(now);
var hospitals = parseJsonFile(hospitalPath);
var departments = parseJsonFile(departmentPath);
var schedules = parseJsonFile(schedulePath);
if (!Array.isArray(hospitals)) {
  hospitals = [hospitals];
}

db.Hospital.drop();
db.Department.drop();
db.Schedule.drop();

hospitals.forEach(function (hospital, index) {
  delete hospital._id;
  hospital.createTime = now;
  hospital.updateTime = now;
  hospital.isDeleted = 0;
  hospital.status = 1;
  hospital._class = "com.atguigu.yygh.model.hosp.Hospital";
  if (hospital.bookingRule) {
    hospital.bookingRule.cycle = Number(hospital.bookingRule.cycle || 10);
    hospital.bookingRule.quitDay = Number(hospital.bookingRule.quitDay || -1);
  }
  db.Hospital.insert(hospital);
});

departments.forEach(function (department, index) {
  delete department._id;
  department.createTime = now;
  department.updateTime = now;
  department.isDeleted = 0;
  department._class = "com.atguigu.yygh.model.hosp.Department";
  db.Department.insert(department);
});

var groupedDayIndex = {};
schedules.forEach(function (schedule, index) {
  insertSchedule(schedule, index, schedule.depcode, schedule.hosScheduleId);
});

var demoDepartmentNames = ["呼吸内科", "消化内科", "心内科", "神经科", "耳鼻喉科", "普通内科"];
var demoDepartments = [];
demoDepartmentNames.forEach(function (name) {
  var matched = departments.find(function (department) {
    return department.depname && department.depname.indexOf(name) >= 0;
  });
  if (matched && demoDepartments.indexOf(matched.depcode) < 0) {
    demoDepartments.push(matched.depcode);
  }
});

demoDepartments.forEach(function (depcode, depIndex) {
  schedules.slice(0, 21).forEach(function (schedule, index) {
    var cloned = Object.assign({}, schedule);
    insertSchedule(cloned, schedules.length + depIndex * 1000 + index, depcode, "DEMO-" + depcode + "-" + schedule.hosScheduleId);
  });
});

function insertSchedule(schedule, index, depcode, hosScheduleId) {
  if (!groupedDayIndex[depcode]) {
    groupedDayIndex[depcode] = 0;
  }
  var oldDate = schedule.workDate;
  if (!groupedDayIndex[depcode + ":" + oldDate]) {
    groupedDayIndex[depcode] += 1;
    groupedDayIndex[depcode + ":" + oldDate] = groupedDayIndex[depcode];
  }
  var offset = groupedDayIndex[depcode + ":" + oldDate];
  var workDate = new Date(today.getTime() + offset * 24 * 60 * 60 * 1000);

  delete schedule._id;
  delete schedule.id;
  schedule.depcode = depcode;
  schedule.hosScheduleId = String(hosScheduleId);
  schedule.workDate = workDate;
  schedule.createTime = now;
  schedule.updateTime = now;
  schedule.isDeleted = 0;
  schedule.status = 1;
  schedule.amount = Number(schedule.amount || 0);
  schedule._class = "com.atguigu.yygh.model.hosp.Schedule";
  db.Schedule.insert(schedule);
}

db.Hospital.createIndex({ hoscode: 1 }, { unique: true });
db.Hospital.createIndex({ hosname: 1 });
db.Department.createIndex({ hoscode: 1 });
db.Department.createIndex({ depcode: 1 }, { unique: true });
db.Schedule.createIndex({ hoscode: 1 });
db.Schedule.createIndex({ depcode: 1 });
db.Schedule.createIndex({ hosScheduleId: 1 });
db.Schedule.createIndex({ hoscode: 1, depcode: 1, workDate: 1 });

printjson({
  importedAt: now,
  sampleWorkDate: yyyyMMdd(new Date(today.getTime() + 24 * 60 * 60 * 1000)),
  Hospital: db.Hospital.count(),
  Department: db.Department.count(),
  Schedule: db.Schedule.count()
});
