import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("gps_trace_quality", Path(__file__).with_name("gps_trace_quality.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class GpsTraceQualityTest(unittest.TestCase):
    def test_point_on_route_has_near_zero_cross_track_error(self):
        route = [(28.0, -16.0), (28.0, -15.999)]
        self.assertLess(MODULE.nearest_route_distance_m((28.0, -15.9995), route), 0.1)

    def test_summary_reports_off_route_samples_and_jumps(self):
        route = [(28.0, -16.0), (28.0, -15.999)]
        samples = [
            {"time": 1000, "lat": 28.0, "lon": -16.0, "accuracy": 5.0,
             "row": {"match_status": "MATCHED", "route_distance_m": "2.0"}},
            {"time": 2000, "lat": 28.001, "lon": -15.999, "accuracy": 8.0,
             "row": {"match_status": "NO_CONFIDENT_MATCH", "route_distance_m": ""}},
        ]
        result = MODULE.summarize_trace(samples, route)
        self.assertEqual(result["samples"], 2)
        self.assertEqual(result["app_matched_percent"], 50.0)
        self.assertEqual(result["cross_track_over_25m_percent"], 50.0)
        self.assertEqual(result["implausible_jump_count_over_55mps"], 1)

    def test_reads_route_gpx_and_diagnostic_csv(self):
        with tempfile.TemporaryDirectory() as folder:
            gpx = Path(folder) / "route.gpx"
            csv_path = Path(folder) / "trace.csv"
            gpx.write_text('<gpx><trk><trkseg><trkpt lat="28.0" lon="-16.0"/><trkpt lat="28.0" lon="-15.999"/></trkseg></trk></gpx>', encoding="utf-8")
            csv_path.write_text(
                "timestamp_epoch_ms,latitude,longitude,accuracy_m,route_distance_m,match_status\n"
                "1000,28.0,-16.0,5,1,MATCHED\n", encoding="utf-8")
            report = MODULE.summarize_trace(MODULE.read_trace_csv(csv_path), MODULE.read_route_gpx(gpx))
            self.assertEqual(report["samples"], 1)


if __name__ == "__main__":
    unittest.main()
