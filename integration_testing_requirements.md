
## 1. Integration Testing
- Integration tests must cover all major workflows between system components.
- Tests should validate data flow and API interactions.
- All integration tests must pass.
- middlewaresw must start socket server on port 5555.
- mwclientwithgui must connect to middlewaresw socket server port 5555.
- Temperature (temperature) range must be -50..500 in logs.
- Rounds Per Minute (rpm) range must be 0..8000 in logs.
- Oil pressure (oil_pressure) range must be 0..200 in logs.
