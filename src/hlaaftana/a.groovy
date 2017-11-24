t = 0
l = 0
new File('kismet/').eachFileRecurse(groovy.io.FileType.FILES) { it ->
  t += it.text.size()
}
println """$l lines
$t characters"""
// 2291 lines
// 89998 characters