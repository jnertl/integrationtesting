

# Integration Testing Requirements

## 1. Test Coverage
- Integration tests must cover all major workflows and interactions between system components.
- Tests should validate both data flow and API interactions across component boundaries.

## 2. Test Success Criteria
- All integration tests must pass without errors.
- The application must not crash during testing. Any segfaults or abnormal terminations must be detected and reported.

## 3. Component Startup and Connectivity
- `middlewaresw` must successfully start and open a socket server on port 5555.
- `mwclientwithgui` must connect to the `middlewaresw` socket server on port 5555.

## 4. Data Validation in Logs
- Temperature (`temperature`) values in logs must be within the range -50 to 500.
- Rounds Per Minute (`rpm`) values in logs must be within the range 0 to 8000.
- Oil Pressure (`oil_pressure`) values in logs must be within the range 0 to 200.

## 5. Reporting
- All test results, including failures and detected crashes, must be clearly reported in the test logs.

## 6. Test Framework
- All integration tests must be implemented using the Robot Framework to ensure consistency and maintainability.
