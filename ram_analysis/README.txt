The tools used in the RAM analysis were nm, valgrind massif and valgrind callgrind, along with some su_merge.py and callgrind_path_parser.py in the mbedtls-test repo to parse the data.

The steps followed were:
  1. The library was built with GCC's -fstack-usage option enabled. The resulting .su files were combined into a single file using su_merge.py for easier use later. This gives the stack usage of all functions in the library.

  2. For each program we want to examine:
    a. Measure statically allocated RAM usage:
      nm --demangle --line-numbers --print-size --numeric-sort <program>
      The lines containing B or b give byte counts for symbols in the uninitialized data section (BSS), while lines containing D or d give byte counts for symbols in the initialized data section.

    b. Measure heap usage by the program: 
      valgrind --tool=massif --stacks=yes --time-unit=B --max-snapshots=1000 --massif-out-file=<massif_output_file> <program> <program_args>
      ms_print <massif_output_file>
       This gives heap usage over time for the program. The --stacks option gives stack usage over time as well, however it will not give details on where this stack is being used. The massif output file is not easily readable, running ms_print on the massif output file provides a more human readable format. The output shows heap (and stack) usage on a snapshot basis, with some of the snapshots having details on the heap usage. The peak usage snapshot will always be a detailed snapshot.

    c. Measure stack usage by the program:
      valgrind --tool=callgrind --separate-callers=100 --callgrind-out-file=<massif_output_file> <program> <program_args>
      This gives the call graph of the program. The --separate-callers should be set to a number larger than the maximum function call depth of the program, 100 was more than sufficient for the example programs. This ensures that we can easily see only the actual paths taken through the code. The output of this is parsed using callgrind_path_parser.py along with the merged .su file to calculate the stack usage for each of these paths. The output of callgrind_path_parser.py is a list of all the function call paths, along with the total stack usage of these paths and each of the functions in the path.
