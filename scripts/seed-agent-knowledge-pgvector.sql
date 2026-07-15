INSERT INTO medical_knowledge_chunk(source_type, hospital_code, hospital_name, department, title, content, metadata_json)
VALUES
('DEMO_SEED', '1000_0', '北京协和医院', '呼吸内科', '呼吸内科导诊',
 '咳嗽、咽痛、发热、鼻塞、流涕、咳痰、喘息等呼吸系统症状，普通情况可优先考虑呼吸内科；若出现严重呼吸困难、口唇发紫、胸痛或意识异常，应优先急诊。',
 '{"safety_level":"general_triage"}'),
('DEMO_SEED', '1000_0', '北京协和医院', '耳鼻喉科', '耳鼻喉科导诊',
 '以咽痛、声音嘶哑、鼻塞流涕、耳痛、听力下降、鼻出血等耳鼻咽喉局部症状为主时，可考虑耳鼻喉科；伴高热、呼吸困难时应及时急诊评估。',
 '{"safety_level":"general_triage"}'),
('DEMO_SEED', '1000_0', '北京协和医院', '消化内科', '消化内科导诊',
 '腹痛、腹泻、恶心呕吐、反酸、胃痛、腹胀、便秘等消化道症状可优先考虑消化内科；剧烈腹痛、呕血、黑便、持续高热或脱水明显时应优先急诊。',
 '{"safety_level":"general_triage"}'),
('DEMO_SEED', '1000_0', '北京协和医院', '心血管内科', '心血管内科导诊',
 '心悸、胸闷、活动后气短、血压异常等可考虑心血管内科；胸痛压榨感、严重胸闷、大汗、濒死感、晕厥等需立即急诊或拨打 120。',
 '{"safety_level":"emergency_sensitive"}'),
('DEMO_SEED', '1000_0', '北京协和医院', '神经内科', '神经内科导诊',
 '头痛、头晕、肢体麻木、记忆下降、睡眠障碍等可考虑神经内科；突发偏瘫、口角歪斜、言语不清、抽搐、意识障碍或剧烈头痛需急诊。',
 '{"safety_level":"emergency_sensitive"}')
ON CONFLICT DO NOTHING;
