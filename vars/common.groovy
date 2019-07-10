import groovy.transform.Field

@Field one_platform = ["debian-9-x64"]
@Field linux_platforms = ["debian-9-i386", "debian-9-x64"]
@Field bsd_platforms = ["freebsd"]
@Field bsd_compilers = ["clang"]
@Field windows_platforms = ['windows']
@Field coverity_platforms = ['coverity && gcc']
@Field windows_compilers = ['cc']
@Field all_compilers = ['gcc', 'clang']
@Field gcc_compilers = ['gcc']
@Field asan_compilers = ['clang']
@Field coverity_compilers = ['gcc']
