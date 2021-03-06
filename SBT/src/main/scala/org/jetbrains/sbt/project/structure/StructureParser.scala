package org.jetbrains.sbt
package project.structure

import scala.xml.Node
import java.io.File
import FS._

/**
 * @author Pavel Fatin
 */

object StructureParser {
  def parse(node: Node, home: File): Structure = {
    implicit val fs = new FS(home)

    val project = (node \ "project").map(parseProject)
    val repository = (node \ "repository").headOption.map(parseRepository)

    Structure(project, repository)
  }

  private def parseProject(node: Node)(implicit fs: FS): Project = {
    val id = (node \ "id").text
    val name = (node \ "name").text
    val organization = (node \ "organization").text
    val version = (node \ "version").text
    val base = new File((node \ "base").text)
    val build = parseBuild(node ! "build")(fs.withBase(base))
    val configurations = (node \ "configuration").map(parseConfiguration(_)(fs.withBase(base)))
    val java = (node \ "java").headOption.map(parseJava(_)(fs.withBase(base)))
    val scala = (node \ "scala").headOption.map(parseScala(_)(fs.withBase(base)))
    val dependencies = parseDependencies(node)

    Project(id, name, organization, version, base, build, configurations, java, scala, dependencies)
  }

  private def parseBuild(node: Node)(implicit fs: FS): Build = {
    val classpath = (node \ "classes").map(e => file(e.text))
    val imports = (node \ "import").map(_.text)

    Build(classpath, imports)
  }

  private def parseJava(node: Node)(implicit fs: FS): Java = {
    val home = (node \ "home").headOption.map(e => file(e.text))
    val options = (node \ "option").map(_.text)

    Java(home, options)
  }

  private def parseScala(node: Node)(implicit fs: FS): Scala = {
    val version = (node \ "version").text
    val library = file((node \ "library").text)
    val compiler = file((node \ "compiler").text)
    val extra = (node \ "extra").map(e => file(e.text))
    val options = (node \ "option").map(_.text)

    Scala(version, library, compiler, extra, options)
  }

  private def parseConfiguration(node: Node)(implicit fs: FS): Configuration = {
    val id = (node \ "@id").text
    val sources = (node \ "sources").map(parseDirectory)
    val resources = (node \ "resources").map(parseDirectory)
    val classes = file((node ! "classes").text)

    Configuration(id, sources, resources, classes)
  }

  private def parseDirectory(node: Node)(implicit fs: FS): Directory = {
    val managed = (node \ "@managed").headOption.exists(_.text.toBoolean)
    Directory(file(node.text), managed)
  }

  private def parseDependencies(node: Node)(implicit fs: FS): Dependencies = {
    val projects = (node \ "project").map(parseProjectDependency)
    val modules = (node \ "module").map(parseModuleDependency)
    val jars = (node \ "jar").map(e => file(e.text))

    Dependencies(projects, modules, jars)
  }

  private def parseProjectDependency(node: Node): ProjectDependency = {
    val project = node.text
    val configurations = (node \ "@configurations").headOption
            .map(it => parseConfigurations(it.text)).getOrElse(Seq.empty)

    ProjectDependency(project, configurations)
  }

  private def parseModuleDependency(node: Node): ModuleDependency = {
    val id = parseModuleIdentifier(node)
    val configurations = (node \ "@configurations").headOption
            .map(it => parseConfigurations(it.text)).getOrElse(Seq.empty)

    ModuleDependency(id, configurations)
  }

  private def parseConfigurations(s: String) = if (s.isEmpty) Seq.empty else s.split(";").toSeq

  private def parseModuleIdentifier(node: Node): ModuleId = {
    val organization = (node \ "@organization").text
    val name = (node \ "@name").text
    val revision = (node \ "@revision").text

    ModuleId(organization, name, revision)
  }

  private def parseRepository(node: Node)(implicit fs: FS): Repository = {
    val modules = (node \ "module").map { it =>
      val identifier = parseModuleIdentifier(it)

      val binaries = (it \ "jar").map(e => file(e.text))
      val docs = (it \ "doc").map(e => file(e.text))
      val sources = (it \ "src").map(e => file(e.text))

      Module(identifier, binaries, docs, sources)
    }

    Repository(new File("."), modules)
  }

  private implicit class NodeExt(node: Node) {
    def !(name: String): Node = (node \ name) match {
      case Seq() => throw new RuntimeException(s"No $name node in $node")
      case Seq(child) => child
      case _ => throw new RuntimeException(s"Multiple $name nodes in $node")
    }
  }
}

private case class FS(home: File, base: Option[File] = None) {
  def withBase(base: File): FS = copy(base = Some(base))
}

private object FS {
  def file(path: String)(implicit fs: FS): File = {
    if (path.startsWith("~/")) {
      new File(fs.home, path.substring(2))
    } else {
      fs.base.map(new File(_, path)).getOrElse(new File(path))
    }
  }
}
