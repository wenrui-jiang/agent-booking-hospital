import unittest
from datetime import date
from unittest.mock import patch

from app.main import (
    answer_repeats_known_questions,
    forward_progress_answer,
    infer_forward_stage,
    merge_slots_from_messages,
    missing_booking_slots,
)


class ForwardProgressTest(unittest.TestCase):
    def test_booking_slots_advance_to_schedule_search(self):
        slots = {}
        messages = [
            {"role": "user", "content": "我最近一直失眠，然后心跳加快，压力很大"},
            {"role": "assistant", "content": "建议优先考虑心内科或精神心理科。"},
            {"role": "user", "content": "北京协和，明天吧"},
        ]

        with patch("app.main.date") as mock_date:
            mock_date.today.return_value = date(2026, 7, 8)
            mock_date.side_effect = lambda *args, **kwargs: date(*args, **kwargs)
            merge_slots_from_messages(slots, messages, "就按照你的推荐，心内科，就诊人是我自己")

        self.assertEqual(slots["hosname"], "北京协和医院")
        self.assertEqual(slots["workDate"], "2026-07-09")
        self.assertEqual(slots["depname"], "心内科")
        self.assertEqual(slots["patient"], "本人")
        self.assertEqual(infer_forward_stage("DEPARTMENT_RECOMMENDING", slots, "心内科"), "BOOKING_SEARCHING")
        self.assertNotIn("医院", missing_booking_slots(slots))
        self.assertNotIn("科室", missing_booking_slots(slots))
        self.assertTrue(answer_repeats_known_questions("请问您想去哪个医院、想看哪个科室？", slots))
        self.assertIn("继续帮你查询可预约号源", forward_progress_answer({"slots": slots}))


if __name__ == "__main__":
    unittest.main()
