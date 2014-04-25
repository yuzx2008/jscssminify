jscssminify
===========

需要配合 Ant 使用：

<taskdef name="JsMinifyTask" classname="net.sf.yuzx.minify.ant.JsMinifyTask" classpathref="ant.classpath" />
<taskdef name="CssMinifyTask" classname="net.sf.yuzx.minify.ant.CssMinifyTask" classpathref="ant.classpath" />

<JsMinifyTask verbose="true" failonerror="true" quiet="false" encoding="UTF-8" todir="${js.dest.dir}/minify_test">
  <mapper>
    <globmapper from="*.js" to="*.min.js" casesensitive="yes" />
  </mapper>
  <fileset dir="${basedir}/web/js/minify_test">
    <include name="**/*.js" />
  </fileset>
</JsMinifyTask>

<CssMinifyTask verbose="true" failonerror="true" quiet="false" encoding="UTF-8" todir="${css.dest.dir}/minify_test">
  <mapper>
    <globmapper from="*.css" to="*.min.css" casesensitive="yes" />
  </mapper>
  <fileset dir="${basedir}/web/css/minify_test">
    <include name="**/*.css" />
  </fileset>
</CssMinifyTask>

