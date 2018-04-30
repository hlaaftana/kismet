import groovy.io.FileType

def t = 0, l = 0
new File('kismet/').eachFileRecurse(FileType.FILES) { it ->
  l += it.readLines().findAll().size()
  t += it.text.size()
}
println """$l lines
$t characters"""
// 2291 lines
// 89998 characters