import groovy.io.FileType

def t = 0, l = 0
new File('kismet/').eachFileRecurse(FileType.FILES) { it ->
  l += it.readLines().findAll().size()
  t += it.text.size()
}
println """$l lines
$t characters"""
// april 30 2018:
// 2291 lines
// 89998 characters
// june 27 2018:
// 5439 lines
// 211328 characters