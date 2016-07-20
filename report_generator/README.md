# mbed TLS report generator

The report generator is a python script that accepts test output in csv format and produces test reports in ASCII and PDF formats.

## Software requirements

* Python 2.7
* LibreOffice Writer (for PDF reports only)
* Pandoc (for PDF reports only)

## Running the script

To run the script use a command as follows from the directory `report_generator/`:

```
./generate-test-report.py -f <INPUT_CSV> -p <OUTPUT_PDF>
```

For more information run

```
./generate-test-report.py --help
```
