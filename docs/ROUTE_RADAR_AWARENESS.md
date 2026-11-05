# Route-wide radar awareness

## Retrieval strategy
The active route is sampled at regular intervals and queried in bounded Overpass batches. Each sample uses an overlapping search circle so batch boundaries do not leave gaps. Returned OSM objects are deduplicated by node ID and projected onto the route geometry.

## Supported enforcement types
- `highway=speed_camera` and `man_made=speed_camera`
- `enforcement=maxspeed`
- `enforcement=average_speed`
- `enforcement=traffic_signals`, including device members of OSM enforcement relations

Ordinary traffic lights are not treated as enforcement cameras.

## False-positive controls
Candidates must fall within the route corridor. Numeric or cardinal direction tags are compared with the route bearing; unknown `forward/backward` values are retained because the node-only response does not include the source way orientation. The existing proximity alert additionally requires a camera ahead on the route and within its alert window.

## User disclosure
The route briefing reports distances from the route origin and explicitly identifies OSM as community-maintained, incomplete data. No result is a guarantee that a road has no enforcement device.

## Operational limits
Queries are sequential, bounded and use alternate public Overpass endpoints. If route-wide retrieval fails, the app keeps the existing local-area radar refresh and alerts. Public Overpass availability and response times are not guaranteed. Field validation is still required.
